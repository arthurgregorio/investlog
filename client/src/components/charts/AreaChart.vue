<script setup lang="ts">
/* Smooth area + line chart that fills its container width via viewBox.
   Catmull-Rom -> bezier smoothing; gradient id is sanitized so a CSS-var
   color (e.g. var(--primary)) still produces a valid url(#id) reference. */
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    data: number[]
    color?: string
    height?: number
    showGrid?: boolean
    fmtY?: (v: number) => string
    xLabels?: string[]
  }>(),
  { color: '#2fb344', height: 240, showGrid: true },
)

const W = 800

const chart = computed(() => {
  const data = props.data
  const H = props.height
  const padL = 56, padR = 16, padT = 12, padB = props.xLabels ? 30 : 28
  const min = Math.min(...data), max = Math.max(...data)
  const range = max - min || 1
  const pad = range * 0.15
  const lo = min >= 0 ? Math.max(0, min - pad) : min - pad
  const hi = max + pad
  const x = (i: number) => padL + (i / (data.length - 1)) * (W - padL - padR)
  const y = (v: number) => padT + (1 - (v - lo) / (hi - lo)) * (H - padT - padB)

  const pts = data.map((v, i) => [x(i), y(v)] as const)
  let dPath = `M ${pts[0][0]},${pts[0][1]}`
  for (let i = 0; i < pts.length - 1; i++) {
    const p0 = pts[i - 1] || pts[i]
    const p1 = pts[i]
    const p2 = pts[i + 1]
    const p3 = pts[i + 2] || p2
    const c1x = p1[0] + (p2[0] - p0[0]) / 6
    const c1y = p1[1] + (p2[1] - p0[1]) / 6
    const c2x = p2[0] - (p3[0] - p1[0]) / 6
    const c2y = p2[1] - (p3[1] - p1[1]) / 6
    dPath += ` C ${c1x},${c1y} ${c2x},${c2y} ${p2[0]},${p2[1]}`
  }
  const area = `${dPath} L ${x(data.length - 1)},${H - padB} L ${x(0)},${H - padB} Z`
  const gid = 'ag' + props.color.replace(/[^a-z0-9]/gi, '')

  const ticks = 4
  const gridY = Array.from({ length: ticks + 1 }, (_, i) => lo + (hi - lo) * (i / ticks))
  const last = pts[pts.length - 1]

  const xTicks: { x: number; anchor: string; label: string }[] = []
  if (props.xLabels) {
    const n = props.xLabels.length
    const step = Math.max(1, Math.ceil(n / 6))
    for (let i = 0; i < n; i += step) {
      xTicks.push({ x: x(i), anchor: i === 0 ? 'start' : 'middle', label: props.xLabels[i] })
    }
  }

  return { H, padL, padR, padB, dPath, area, gid, gridY, last, x, y, xTicks }
})
</script>

<template>
  <svg
    :viewBox="`0 0 ${W} ${chart.H}`"
    width="100%"
    :height="chart.H"
    preserveAspectRatio="none"
    style="display: block; overflow: visible"
  >
    <defs>
      <linearGradient :id="chart.gid" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" :stop-color="color" stop-opacity="0.22" />
        <stop offset="100%" :stop-color="color" stop-opacity="0" />
      </linearGradient>
    </defs>
    <template v-if="showGrid">
      <g v-for="(gv, i) in chart.gridY" :key="i">
        <line
          :x1="chart.padL"
          :x2="W - chart.padR"
          :y1="chart.y(gv)"
          :y2="chart.y(gv)"
          stroke="var(--chart-grid)"
          stroke-width="1"
          stroke-dasharray="2 4"
        />
        <text
          :x="chart.padL - 10"
          :y="chart.y(gv) + 4"
          text-anchor="end"
          font-size="12"
          fill="var(--text-muted)"
          font-family="inherit"
        >
          {{ fmtY ? fmtY(gv) : Math.round(gv) }}
        </text>
      </g>
    </template>
    <path :d="chart.area" :fill="`url(#${chart.gid})`" />
    <path
      :d="chart.dPath"
      fill="none"
      :stroke="color"
      stroke-width="2.5"
      stroke-linejoin="round"
      stroke-linecap="round"
      vector-effect="non-scaling-stroke"
    />
    <text
      v-for="(t, i) in chart.xTicks"
      :key="`x${i}`"
      :x="t.x"
      :y="chart.H - 8"
      :text-anchor="t.anchor"
      font-size="11.5"
      fill="var(--text-muted)"
      font-family="inherit"
    >
      {{ t.label }}
    </text>
    <circle :cx="chart.last[0]" :cy="chart.last[1]" r="4.5" :fill="color" stroke="var(--surface)" stroke-width="2.5" />
  </svg>
</template>
