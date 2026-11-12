<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { ChartArea, ChartOptions, Plugin, ScriptableContext } from 'chart.js'
import {
  CategoryScale,
  Chart,
  Filler,
  LinearScale,
  LineController,
  LineElement,
  PointElement,
  Tooltip,
} from 'chart.js'
import { hexToRgba, resolveColor, useChartThemeSync } from '@/composables/useChartTheme'

Chart.register(
  LineController,
  LineElement,
  PointElement,
  LinearScale,
  CategoryScale,
  Filler,
  Tooltip,
)

const props = withDefaults(
  defineProps<{
    ownMoney: number[]
    result: number[]
    labels: string[]
    markerIndex?: number | null
    markerLabel?: string
    fmtY?: (value: number) => string
  }>(),
  {
    markerIndex: null,
    markerLabel: '',
    fmtY: (value: number) => String(value),
  },
)

const MARKER_LABEL_GAP = 6

const canvasElement = ref<HTMLCanvasElement | null>(null)
let chart: Chart<'line'> | null = null

let colors = resolveColors()

function resolveColors() {
  return {
    primary: resolveColor('var(--primary)'),
    up: resolveColor('var(--up)'),
    down: resolveColor('var(--down)'),
    grid: resolveColor('var(--chart-grid)'),
    textMuted: resolveColor('var(--text-muted)'),
    surface: resolveColor('var(--surface)'),
  }
}

function totals(): number[] {
  return props.ownMoney.map((value, index) => value + (props.result[index] ?? 0))
}

function yRange(values: number[]) {
  const min = Math.min(...values)
  const max = Math.max(...values)
  const padding = (max - min || 1) * 0.12
  return { low: min >= 0 ? 0 : min - padding, high: max + padding }
}

const decorationsPlugin: Plugin<'line'> = {
  id: 'patrimonyDecorations',
  beforeDraw(chartInstance) {
    const yScale = chartInstance.scales.y
    if (!yScale) return
    const { ctx, chartArea } = chartInstance
    ctx.save()
    ctx.strokeStyle = colors.grid
    ctx.lineWidth = 1
    ctx.setLineDash([2, 4])
    for (const tick of yScale.ticks) {
      const y = yScale.getPixelForValue(tick.value)
      ctx.beginPath()
      ctx.moveTo(chartArea.left, y)
      ctx.lineTo(chartArea.right, y)
      ctx.stroke()
    }
    ctx.restore()
  },
  afterDatasetsDraw(chartInstance) {
    const xScale = chartInstance.scales.x
    if (props.markerIndex == null || !xScale) return
    const { ctx, chartArea } = chartInstance
    const x = xScale.getPixelForValue(props.markerIndex)
    ctx.save()
    ctx.strokeStyle = colors.textMuted
    ctx.lineWidth = 1
    ctx.setLineDash([3, 4])
    ctx.beginPath()
    ctx.moveTo(x, chartArea.top)
    ctx.lineTo(x, chartArea.bottom)
    ctx.stroke()
    ctx.setLineDash([])
    ctx.fillStyle = colors.textMuted
    ctx.font = '11.5px Inter, sans-serif'
    const { textAlign, textX } = markerLabelPlacement(ctx, x, chartArea)
    ctx.textAlign = textAlign
    ctx.textBaseline = 'top'
    ctx.fillText(props.markerLabel, textX, chartArea.top)
    ctx.restore()
  },
}

function markerLabelPlacement(context: CanvasRenderingContext2D, x: number, chartArea: ChartArea) {
  const labelWidth = context.measureText(props.markerLabel).width
  const before = { textAlign: 'right' as const, textX: x - MARKER_LABEL_GAP }
  const after = { textAlign: 'left' as const, textX: x + MARKER_LABEL_GAP }
  const roomBefore = before.textX - chartArea.left
  const roomAfter = chartArea.right - after.textX
  if (labelWidth <= roomBefore) return before
  if (labelWidth <= roomAfter) return after
  return roomBefore >= roomAfter ? before : after
}

function buildOptions(low: number, high: number): ChartOptions<'line'> {
  return {
    responsive: true,
    maintainAspectRatio: false,
    animation: false,
    interaction: { mode: 'index', intersect: false },
    plugins: {
      legend: { display: false },
      tooltip: { callbacks: { label: (context) => props.fmtY(Number(context.parsed.y)) } },
    },
    scales: {
      y: {
        type: 'linear',
        min: low,
        max: high,
        grid: { display: false },
        border: { display: false },
        ticks: {
          count: 5,
          callback: (value) => props.fmtY(Number(value)),
          color: colors.textMuted,
          font: { size: 12 },
        },
      },
      x: {
        type: 'category',
        grid: { display: false },
        border: { display: false },
        ticks: {
          maxRotation: 0,
          autoSkip: true,
          maxTicksLimit: 6,
          color: colors.textMuted,
          font: { size: 11.5 },
        },
      },
    },
  }
}

function createChart() {
  if (!canvasElement.value) return
  const totalValues = totals()
  const lastResult = props.result[props.result.length - 1] ?? 0
  const resultColor = lastResult >= 0 ? colors.up : colors.down
  const { low, high } = yRange([...props.ownMoney, ...totalValues])

  chart = new Chart(canvasElement.value, {
    type: 'line',
    data: {
      labels: props.labels,
      datasets: [
        {
          data: props.ownMoney,
          borderColor: colors.primary,
          borderWidth: 2.5,
          tension: 0.4,
          fill: 'origin',
          backgroundColor: (context: ScriptableContext<'line'>) => {
            const { ctx, chartArea } = context.chart
            if (!chartArea) return undefined
            const gradient = ctx.createLinearGradient(0, chartArea.top, 0, chartArea.bottom)
            gradient.addColorStop(0, hexToRgba(colors.primary, 0.22))
            gradient.addColorStop(1, hexToRgba(colors.primary, 0))
            return gradient
          },
          pointRadius: 0,
        },
        {
          data: totalValues,
          borderColor: resultColor,
          borderWidth: 2.5,
          tension: 0.4,
          fill: {
            target: 0,
            above: hexToRgba(colors.up, 0.2),
            below: hexToRgba(colors.down, 0.2),
          },
          pointRadius: (context: ScriptableContext<'line'>) =>
            context.dataIndex === totalValues.length - 1 ? 4.5 : 0,
          pointBackgroundColor: resultColor,
          pointBorderColor: colors.surface,
          pointBorderWidth: 2.5,
        },
      ],
    },
    options: buildOptions(low, high),
    plugins: [decorationsPlugin],
  })
}

function rebuild() {
  colors = resolveColors()
  chart?.destroy()
  createChart()
}

onMounted(createChart)

watch(
  [
    () => props.ownMoney,
    () => props.result,
    () => props.labels,
    () => props.markerIndex,
    () => props.fmtY,
  ],
  rebuild,
  { deep: true },
)

useChartThemeSync(rebuild)

onBeforeUnmount(() => chart?.destroy())
</script>

<template>
  <div class="patrimony-chart is-relative">
    <canvas ref="canvasElement"></canvas>
  </div>
</template>

<style scoped>
.patrimony-chart {
  flex: 1;
  min-height: 240px;
}
</style>
