<script setup lang="ts">
/* Smooth area + line chart, rendered via Chart.js. Canvas sizes to real backing-store
   pixels (no SVG viewBox stretch), so axis-label glyphs are never non-uniformly scaled —
   that distortion is what made "US$" render as "JS$" in the previous SVG implementation. */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { ChartOptions, Plugin } from 'chart.js'
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
    data: number[]
    color?: string
    height?: number
    showGrid?: boolean
    fmtY?: (v: number) => string
    xLabels?: string[]
  }>(),
  {
    color: '#2fb344',
    height: 240,
    showGrid: true,
    fmtY: (v: number) => String(v),
    xLabels: () => [],
  },
)

const canvasElement = ref<HTMLCanvasElement | null>(null)
let chart: Chart<'line'> | null = null

let lineColor = resolveColor(props.color)
let gridColor = resolveColor('var(--chart-grid)')
let textMutedColor = resolveColor('var(--text-muted)')
let surfaceColor = resolveColor('var(--surface)')

function refreshResolvedColors() {
  lineColor = resolveColor(props.color)
  gridColor = resolveColor('var(--chart-grid)')
  textMutedColor = resolveColor('var(--text-muted)')
  surfaceColor = resolveColor('var(--surface)')
}

function computeYRange(data: number[]) {
  const min = Math.min(...data)
  const max = Math.max(...data)
  const range = max - min || 1
  const pad = range * 0.15
  const lo = min >= 0 ? Math.max(0, min - pad) : min - pad
  const hi = max + pad
  return { lo, hi }
}

function formatYTick(value: number) {
  return props.fmtY ? props.fmtY(value) : String(Math.round(value))
}

// Chart.js v4 dropped the ability to dash the gridlines crossing the chart area
// (GridLineOptions has no dash option; BorderOptions.dash only dashes the single
// axis-edge border line) — draw the dashed Y-gridlines ourselves instead.
const dashedYGridPlugin: Plugin<'line'> = {
  id: 'dashedYGrid',
  beforeDraw(chartInstance) {
    if (!props.showGrid) return
    const yScale = chartInstance.scales.y
    if (!yScale) return
    const { ctx, chartArea } = chartInstance
    ctx.save()
    ctx.strokeStyle = gridColor
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
}

function buildOptions(lo: number, hi: number): ChartOptions<'line'> {
  return {
    responsive: true,
    maintainAspectRatio: false,
    animation: false,
    interaction: { mode: 'index', intersect: false },
    plugins: {
      legend: { display: false },
      tooltip: { callbacks: { label: (context) => formatYTick(Number(context.parsed.y)) } },
    },
    scales: {
      y: {
        type: 'linear',
        display: props.showGrid,
        min: lo,
        max: hi,
        grid: { display: false },
        border: { display: false },
        ticks: {
          count: 5,
          callback: (value) => formatYTick(Number(value)),
          color: textMutedColor,
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
          color: textMutedColor,
          font: { size: 11.5 },
        },
      },
    },
  }
}

function createChart() {
  if (!canvasElement.value) return
  const { lo, hi } = computeYRange(props.data)

  chart = new Chart(canvasElement.value, {
    type: 'line',
    data: {
      labels: props.xLabels ?? props.data.map(() => ''),
      datasets: [
        {
          data: props.data,
          borderColor: lineColor,
          borderWidth: 2.5,
          borderCapStyle: 'round',
          borderJoinStyle: 'round',
          fill: true,
          tension: 0.4,
          backgroundColor: (context) => {
            const { ctx, chartArea } = context.chart
            if (!chartArea) return undefined
            const gradient = ctx.createLinearGradient(0, chartArea.top, 0, chartArea.bottom)
            gradient.addColorStop(0, hexToRgba(lineColor, 0.22))
            gradient.addColorStop(1, hexToRgba(lineColor, 0))
            return gradient
          },
          pointRadius: (context) => (context.dataIndex === props.data.length - 1 ? 4.5 : 0),
          pointBackgroundColor: () => lineColor,
          pointBorderColor: () => surfaceColor,
          pointBorderWidth: 2.5,
        },
      ],
    },
    options: buildOptions(lo, hi),
    plugins: [dashedYGridPlugin],
  })
}

onMounted(createChart)

watch(
  [
    () => props.data,
    () => props.xLabels,
    () => props.color,
    () => props.fmtY,
    () => props.showGrid,
  ],
  () => {
    refreshResolvedColors()
    chart?.destroy()
    createChart()
  },
  { deep: true },
)

useChartThemeSync(() => {
  refreshResolvedColors()
  chart?.destroy()
  createChart()
})

onBeforeUnmount(() => chart?.destroy())
</script>

<template>
  <div :style="{ position: 'relative', height: `${height}px` }">
    <canvas ref="canvasElement"></canvas>
  </div>
</template>
