<script setup lang="ts">
/* Donut chart for allocation. Segments render as stroked arcs via
   stroke-dasharray / strokeDashoffset, rotated -90deg to start at 12 o'clock. */
import { computed } from 'vue'

interface Segment {
  value: number
  color: string
  label: string
}

const props = withDefaults(defineProps<{ segments: Segment[]; size?: number; thickness?: number }>(), {
  size: 168,
  thickness: 22,
})

const geometry = computed(() => {
  const r = (props.size - props.thickness) / 2
  const c = props.size / 2
  const total = props.segments.reduce((s, d) => s + d.value, 0) || 1
  const C = 2 * Math.PI * r

  let offset = 0
  const arcs = props.segments.map((d) => {
    const len = (d.value / total) * C
    const arc = { color: d.color, dasharray: `${len} ${C - len}`, dashoffset: -offset }
    offset += len
    return arc
  })
  return { r, c, C, arcs }
})
</script>

<template>
  <div :style="{ position: 'relative', width: `${size}px`, height: `${size}px` }">
    <svg :width="size" :height="size" style="transform: rotate(-90deg)">
      <circle
        :cx="geometry.c"
        :cy="geometry.c"
        :r="geometry.r"
        fill="none"
        stroke="var(--chart-grid)"
        :stroke-width="thickness"
        opacity="0.5"
      />
      <circle
        v-for="(arc, i) in geometry.arcs"
        :key="i"
        :cx="geometry.c"
        :cy="geometry.c"
        :r="geometry.r"
        fill="none"
        :stroke="arc.color"
        :stroke-width="thickness"
        :stroke-dasharray="arc.dasharray"
        :stroke-dashoffset="arc.dashoffset"
        stroke-linecap="butt"
      />
    </svg>
    <div
      style="
        position: absolute;
        inset: 0;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        text-align: center;
      "
    >
      <slot />
    </div>
  </div>
</template>
