<script setup lang="ts">
import { fmt } from '@/composables/useFormat'

defineProps<{
  administrationFeeRate: number | null
  performanceFeeRate: number | null
}>()

function formatFeeRate(rate: number | null): string {
  return rate == null ? '—' : fmt.pct(rate)
}
</script>

<template>
  <div class="fund-fees" data-testid="fund-fees">
    <div class="fund-fee">
      <span class="fund-fee-label">Taxa de administração (% a.a.)</span>
      <span class="fund-fee-value" data-testid="administration-fee-rate">{{
        formatFeeRate(administrationFeeRate)
      }}</span>
    </div>
    <div class="fund-fee">
      <span class="fund-fee-label">Taxa de performance (%)</span>
      <span class="fund-fee-value" data-testid="performance-fee-rate">{{
        formatFeeRate(performanceFeeRate)
      }}</span>
    </div>
  </div>
</template>

<style scoped>
.fund-fees {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 28px;
  margin: 4px 2px 12px;
}

.fund-fee {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.fund-fee-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-muted);
}

.fund-fee-value {
  font-size: 14px;
  font-weight: 700;
}
</style>
