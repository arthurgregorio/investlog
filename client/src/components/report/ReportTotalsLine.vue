<script setup lang="ts">
import GainChip from '@/components/ui/GainChip.vue'
import { fmt } from '@/composables/useFormat'
import { useCurrencyStore } from '@/stores/currency'
import type { ReportTotals } from '@/utils/reportGrouping'

defineProps<{
  totals: ReportTotals
  variant: 'grand' | 'kind' | 'subgroup'
}>()

const currencyStore = useCurrencyStore()
</script>

<template>
  <div
    class="report-subtotal-line"
    :class="{ 'report-grand-total': variant === 'grand', 'is-subgroup': variant === 'subgroup' }"
  >
    <div class="report-figure">
      <span class="report-figure-label">Investido</span>
      <span class="report-figure-value">
        {{ fmt.money(totals.costBasis, currencyStore.displayCurrency) }}
      </span>
    </div>
    <div class="report-figure">
      <span class="report-figure-label">Atual</span>
      <span class="report-figure-value">
        {{ fmt.money(totals.currentValue, currencyStore.displayCurrency) }}
      </span>
    </div>
    <div class="report-figure">
      <span class="report-figure-label">Resultado</span>
      <GainChip :value="totals.gain" :pct="totals.gainPct" :cur="currencyStore.displayCurrency" />
    </div>
  </div>
</template>

<style scoped>
.report-subtotal-line {
  display: flex;
  gap: 20px;
  justify-content: flex-end;
}

.report-figure {
  display: flex;
  flex-direction: column;
  gap: 2px;
  white-space: nowrap;
}

.report-figure-label {
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--text-muted);
  font-weight: 700;
}

.report-figure-value,
.report-figure :deep(.gl) {
  font-size: 14px;
  font-weight: 800;
}

.report-grand-total {
  gap: 12px;
  flex-shrink: 0;
}

.report-grand-total .report-figure {
  gap: 1px;
  white-space: normal;
}

.report-grand-total .report-figure-label {
  font-size: 8px;
  letter-spacing: 0.06em;
}

.is-subgroup .report-figure-label {
  font-size: 9px;
}

.report-grand-total .report-figure-value,
.report-grand-total .report-figure :deep(.gl),
.is-subgroup .report-figure-value,
.is-subgroup .report-figure :deep(.gl) {
  font-size: 12px;
}
</style>
