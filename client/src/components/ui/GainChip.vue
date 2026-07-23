<script setup lang="ts">
import { computed } from 'vue'
import { fmt } from '@/composables/useFormat'

const props = withDefaults(
  defineProps<{
    value: number | null
    pct?: number | null
    cur?: string
    compact?: boolean
    stacked?: boolean
  }>(),
  {
    pct: null,
    cur: 'BRL',
    compact: false,
    stacked: false,
  },
)

const dir = computed(() => {
  if (props.value == null) return 'empty'
  if (props.value > 0.0001) return 'up'
  if (props.value < -0.0001) return 'down'
  return 'flat'
})
</script>

<template>
  <span v-if="value == null" class="gl-empty">—</span>
  <span v-else class="gl" :class="[`gl-${dir}`, { 'gl-stacked': stacked }]">
    <b-icon v-if="dir !== 'flat'" :icon="dir === 'up' ? 'chevron-up' : 'chevron-down'" size="is-small" />
    <span>{{ fmt.moneySigned(value, cur, { compact }) }}</span>
    <span v-if="pct != null" class="gl-pct">{{ fmt.pctSigned(pct) }}</span>
  </span>
</template>
