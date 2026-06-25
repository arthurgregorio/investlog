/* Shared helpers for the Chart.js-based charts. Colors throughout the app are CSS
   custom properties that differ per theme/accent and are toggled live by setting
   data-theme/data-accent on <html> (see App.vue). SVG re-resolves var(...) live;
   canvas cannot, so colors must be resolved once via getComputedStyle and the chart
   explicitly redrawn whenever theme/accent changes. */
import { watch } from 'vue'
import { useAppearanceStore } from '@/stores/appearance'

const CSS_VARIABLE_PATTERN = /^var\((--[\w-]+)\)$/

export function resolveColor(rawColor: string): string {
  const match = rawColor.match(CSS_VARIABLE_PATTERN)
  if (!match) return rawColor
  return getComputedStyle(document.documentElement).getPropertyValue(match[1]).trim()
}

export function hexToRgba(hexColor: string, alpha: number): string {
  const hex = hexColor.replace('#', '')
  const red = parseInt(hex.slice(0, 2), 16)
  const green = parseInt(hex.slice(2, 4), 16)
  const blue = parseInt(hex.slice(4, 6), 16)
  return `rgba(${red}, ${green}, ${blue}, ${alpha})`
}

export function donutCutoutPercent(size: number, thickness: number): string {
  return `${((size / 2 - thickness) / (size / 2)) * 100}%`
}

/** Re-runs `onThemeChange` (re-resolve colors + chart.update()) after the DOM has
    been patched, so it always observes App.vue's data-theme/data-accent write. */
export function useChartThemeSync(onThemeChange: () => void) {
  const appearance = useAppearanceStore()
  watch([() => appearance.dark, () => appearance.accent], onThemeChange, { flush: 'post' })
}
