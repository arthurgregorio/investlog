<script setup lang="ts">
/* Donut chart for allocation, rendered via Chart.js so segment-label text (none here,
   but axis text in the sibling AreaChart) is never subject to non-uniform SVG scaling. */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ArcElement, Chart, DoughnutController, Tooltip } from 'chart.js'
import { donutCutoutPercent, resolveColor, useChartThemeSync } from '@/composables/useChartTheme'

Chart.register(DoughnutController, ArcElement, Tooltip)

interface Segment {
  value: number
  color: string
  label: string
}

const props = withDefaults(defineProps<{ segments: Segment[]; size?: number; thickness?: number }>(), {
  size: 168,
  thickness: 22,
})

const canvasElement = ref<HTMLCanvasElement | null>(null)
let chart: Chart<'doughnut'> | null = null

function createChart() {
  if (!canvasElement.value) return
  chart = new Chart(canvasElement.value, {
    type: 'doughnut',
    data: {
      labels: props.segments.map((segment) => segment.label),
      datasets: [
        {
          data: props.segments.map((segment) => segment.value),
          backgroundColor: props.segments.map((segment) => resolveColor(segment.color)),
          borderWidth: 0,
          spacing: 0,
        },
      ],
    },
    options: {
      cutout: donutCutoutPercent(props.size, props.thickness),
      responsive: true,
      maintainAspectRatio: false,
      animation: false,
      plugins: {
        tooltip: {
          callbacks: {
            label: (context) => {
              const total = (context.dataset.data as number[]).reduce((sum, value) => sum + value, 0)
              const percent = total ? ((Number(context.parsed) / total) * 100).toFixed(1) : '0'
              return `${context.label}: ${percent}%`
            },
          },
        },
      },
    },
  })
}

onMounted(createChart)

watch(
  [() => props.size, () => props.thickness, () => props.segments],
  () => {
    chart?.destroy()
    createChart()
  },
  { deep: true, flush: 'post' },
)

useChartThemeSync(() => {
  if (!chart) return
  chart.data.datasets[0].backgroundColor = props.segments.map((segment) => resolveColor(segment.color))
  chart.update()
})

onBeforeUnmount(() => chart?.destroy())
</script>

<template>
  <div :style="{ position: 'relative', width: `${size}px`, height: `${size}px` }">
    <canvas ref="canvasElement" />
    <div
      style="
        position: absolute;
        inset: 0;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        text-align: center;
        pointer-events: none;
      "
    >
      <slot />
    </div>
  </div>
</template>
