<script setup lang="ts">
import { fmt } from '@/composables/useFormat'

export interface AllocationLegendEntry {
  key: string
  label: string
  name?: string | null
  color: string
  share: string
  value: number
  relativeShare?: number
}

withDefaults(
  defineProps<{ entries: AllocationLegendEntry[]; currency: string; detailed?: boolean }>(),
  { detailed: false },
)
</script>

<template>
  <ul class="alloc-legend" :class="{ 'is-detailed': detailed }" data-testid="allocation-legend">
    <li
      v-for="entry in entries"
      :key="entry.key"
      class="legend-row"
      data-testid="allocation-legend-entry"
    >
      <span class="legend-dot" :style="{ background: entry.color }" />
      <span class="legend-label">
        <span>{{ entry.label }}</span>
        <span v-if="entry.name" class="is-size-7 has-text-grey ml-2">{{ entry.name }}</span>
      </span>
      <progress
        v-if="detailed"
        class="progress mb-0"
        max="100"
        :value="entry.relativeShare"
        :style="{ '--bulma-progress-value-background-color': entry.color }"
      />
      <span class="legend-pct">{{ entry.share }}</span>
      <span class="legend-value">{{
        fmt.money(entry.value, currency, { compact: !detailed })
      }}</span>
    </li>
  </ul>
</template>

<style scoped>
.alloc-legend {
  list-style: none;
  margin: 0;
  padding: 0;
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 11px;
}

.legend-row {
  display: grid;
  grid-template-columns: 13px 1fr auto auto;
  align-items: center;
  gap: 9px;
  font-size: 13px;
}

.legend-dot {
  width: 11px;
  height: 11px;
  border-radius: 4px;
}

.legend-label {
  font-weight: 500;
  color: var(--text-2);
}

.legend-pct {
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.legend-value {
  color: var(--text-muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.alloc-legend.is-detailed {
  gap: 0;
}

.is-detailed .legend-row {
  grid-template-columns: 12px minmax(0, 1.1fr) minmax(60px, 1fr) 64px 110px;
  gap: 12px;
  min-height: 38px;
  padding: 0 4px;
  font-variant-numeric: tabular-nums;
  border-bottom: 1px solid var(--bulma-border-weak);
}

.is-detailed .legend-row:last-child {
  border-bottom: none;
}

.is-detailed .legend-dot {
  width: 12px;
  height: 12px;
  border-radius: 3px;
}

.is-detailed .legend-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
  color: inherit;
}

.is-detailed .legend-pct {
  font-weight: inherit;
  text-align: right;
  color: var(--bulma-grey);
}

.is-detailed .legend-value {
  text-align: right;
  font-size: inherit;
  color: inherit;
}

.is-detailed .progress {
  height: 6px;
}
</style>
