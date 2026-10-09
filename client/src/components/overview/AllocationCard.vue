<script setup lang="ts">
import { computed } from 'vue'
import Card from '@/components/ui/Card.vue'
import CardBody from '@/components/ui/CardBody.vue'
import AllocationLegend from '@/components/charts/AllocationLegend.vue'
import DonutChart from '@/components/charts/DonutChart.vue'
import { fmt } from '@/composables/useFormat'
import type { WalletKindRow } from '@/utils/walletKindRows'

const props = defineProps<{ rows: WalletKindRow[]; totalInvested: number; currency: string }>()

const segments = computed(() => {
  const investedSegments = props.rows
    .filter((row) => row.invested > 0)
    .map((row) => ({ value: row.invested, color: row.accent, label: row.label }))
  return investedSegments.length
    ? investedSegments
    : [{ value: 1, color: 'var(--chart-grid)', label: '—' }]
})

const legendEntries = computed(() =>
  props.rows.map((row) => ({
    key: row.key,
    label: row.label,
    color: row.accent,
    share: props.totalInvested ? fmt.pct((row.invested / props.totalInvested) * 100) : '0%',
    value: row.invested,
  })),
)
</script>

<template>
  <Card class="alloc-card">
    <CardBody>
      <div class="chart-title mb-3">Alocação por tipo</div>
      <div class="alloc-body">
        <DonutChart :segments="segments" :size="156" :thickness="22">
          <div class="donut-center-label">Investido</div>
          <div class="donut-center-value">
            {{ fmt.money(totalInvested, currency, { compact: true }) }}
          </div>
        </DonutChart>
        <AllocationLegend :entries="legendEntries" :currency="currency" />
      </div>
    </CardBody>
  </Card>
</template>

<style scoped>
.alloc-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.alloc-card > :deep(.card-body) {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.alloc-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 18px;
}

.donut-center-label {
  font-size: 11px;
  color: var(--text-muted);
  font-weight: 500;
}

.donut-center-value {
  font-size: 16px;
  font-weight: 800;
  letter-spacing: -0.01em;
  margin-top: 2px;
}
</style>
