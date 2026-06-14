<script setup lang="ts">
/* Tabler-style line icons (24x24, stroke 2, round caps/joins).
   Each entry is a list of path `d` strings — ported from the prototype's icons.jsx. */
import { computed } from 'vue'

export type IconName = keyof typeof ICONS

const ICONS = {
  dashboard: ['M4 4h6v8H4z', 'M4 16h6v4H4z', 'M14 12h6v8h-6z', 'M14 4h6v4h-6z'],
  wallet: ['M17 8V5a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h14a1 1 0 0 1 1 1v8a1 1 0 0 1-1 1H5a2 2 0 0 1-2-2V6', 'M16 12h2'],
  list: ['M9 6h11', 'M9 12h11', 'M9 18h11', 'M5 6v.01', 'M5 12v.01', 'M5 18v.01'],
  exchange: ['M3 8h14l-3 -3', 'M21 16h-14l3 3'],
  star: ['M12 17.75l-6.172 3.245 1.179-6.873-5-4.867 6.9-1 3.086-6.253 3.086 6.253 6.9 1-5 4.867 1.179 6.873z'],
  report: ['M9 5H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2', 'M9 3a2 2 0 0 0-2 2 2 2 0 0 0 2 2h6a2 2 0 0 0 2-2 2 2 0 0 0-2-2z', 'M9 12h6', 'M9 16h4'],
  settings: ['M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 0 0 2.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 0 0 1.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 0 0-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 0 0-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 0 0-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 0 0-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 0 0 1.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z', 'M9 12a3 3 0 1 0 6 0 3 3 0 0 0-6 0'],
  search: ['M10 10m-7 0a7 7 0 1 0 14 0 7 7 0 1 0-14 0', 'M21 21l-6-6'],
  sun: ['M12 12m-4 0a4 4 0 1 0 8 0 4 4 0 1 0-8 0', 'M3 12h1M20 12h1M12 3v1M12 20v1M5.6 5.6l.7.7M18.4 5.6l-.7.7M5.6 18.4l.7-.7M18.4 18.4l-.7-.7'],
  moon: ['M12 3c.132 0 .263 0 .393 0a7.5 7.5 0 0 0 7.92 12.446a9 9 0 1 1 -8.313 -12.454z'],
  plus: ['M12 5v14M5 12h14'],
  chevronDown: ['M6 9l6 6 6-6'],
  chevronUp: ['M6 15l6-6 6 6'],
  chevronRight: ['M9 6l6 6-6 6'],
  chevronLeft: ['M15 6l-6 6 6 6'],
  dots: ['M5 12h.01M12 12h.01M19 12h.01'],
  bell: ['M10 5a2 2 0 1 1 4 0a7 7 0 0 1 4 6v3a4 4 0 0 0 2 3h-16a4 4 0 0 0 2-3v-3a7 7 0 0 1 4-6', 'M9 17v1a3 3 0 0 0 6 0v-1'],
  arrowUp: ['M12 5v14M16 9l-4-4-4 4'],
  arrowDown: ['M12 5v14M8 15l4 4 4-4'],
  trendUp: ['M3 17l6-6 4 4 8-8', 'M14 7h7v7'],
  sort: ['M3 9l4-4 4 4M7 5v14', 'M21 15l-4 4-4-4M17 19V5'],
  grid: ['M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z'],
  tag: ['M7.5 7.5m-1 0a1 1 0 1 0 2 0a1 1 0 1 0-2 0', 'M3 6v5.172a2 2 0 0 0 .586 1.414l7.71 7.71a2.41 2.41 0 0 0 3.408 0l5.592-5.592a2.41 2.41 0 0 0 0-3.408l-7.71-7.71A2 2 0 0 0 11.172 3H6a3 3 0 0 0-3 3z'],
  filter: ['M4 4h16v2.172a2 2 0 0 1-.586 1.414L15 12v7l-6 2v-8.5L4.52 7.572A2 2 0 0 1 4 6.227z'],
  download: ['M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2', 'M7 11l5 5 5-5M12 4v12'],
  pencil: ['M4 20h4l10.5-10.5a2.828 2.828 0 1 0-4-4L4 16v4', 'M13.5 6.5l4 4'],
  menu: ['M4 6h16M4 12h16M4 18h16'],
  x: ['M18 6L6 18M6 6l12 12'],
  check: ['M5 12l5 5L20 7'],
  trash: ['M4 7h16M10 11v6M14 11v6M5 7l1 12a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2l1-12M9 7V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v3'],
  calendar: ['M4 7a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2zM16 3v4M8 3v4M4 11h16'],
  coins: ['M9 14m-5 0a5 5 0 1 0 10 0a5 5 0 1 0 -10 0', 'M14.5 4.4a5 5 0 1 1 .5 9.6', 'M9 11v6M7.5 12.5h3'],
  building: ['M5 21V5a1 1 0 0 1 1-1h8a1 1 0 0 1 1 1v16', 'M15 9h3a1 1 0 0 1 1 1v11', 'M3 21h18M8 8h.01M11 8h.01M8 12h.01M11 12h.01M8 16h.01M11 16h.01'],
  layers: ['M12 3l9 5-9 5-9-5 9-5z', 'M3 13l9 5 9-5'],
  plusCircle: ['M12 12m-9 0a9 9 0 1 0 18 0a9 9 0 1 0 -18 0', 'M9 12h6M12 9v6'],
  folder: ['M4 6a2 2 0 0 1 2-2h4l2 2h6a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2z'],
  repeat: ['M4 12V9a3 3 0 0 1 3-3h12l-3-3M20 12v3a3 3 0 0 1-3 3H5l3 3'],
  info: ['M12 12m-9 0a9 9 0 1 0 18 0a9 9 0 1 0 -18 0', 'M12 8h.01M11 12h1v4h1'],
} satisfies Record<string, string[]>

const props = withDefaults(defineProps<{ name: IconName; size?: number; stroke?: number }>(), {
  size: 24,
  stroke: 2,
})

const paths = computed(() => ICONS[props.name] ?? [])
</script>

<template>
  <svg
    xmlns="http://www.w3.org/2000/svg"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    :stroke-width="stroke"
    stroke-linecap="round"
    stroke-linejoin="round"
  >
    <path v-for="(d, i) in paths" :key="i" :d="d" />
  </svg>
</template>
