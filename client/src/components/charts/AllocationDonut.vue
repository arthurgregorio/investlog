<script setup lang="ts">
import { computed, ref } from 'vue'
import DonutChart from '@/components/charts/DonutChart.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import { fmt } from '@/composables/useFormat'
import { resolveColor, useChartThemeSync } from '@/composables/useChartTheme'
import { accentShades, computeAllocation, type AllocationMetric } from '@/utils/allocation'
import { WALLET_TYPES } from '@/utils/walletTypes'
import type { HoldingRow, WalletKind } from '@/types'

const RING_SIZE = 150
const RING_THICKNESS = 20
const OTHERS_COLOR = '#9aa3ad'

const props = defineProps<{ rows: HoldingRow[]; kind: WalletKind; currency: string }>()

const METRIC_OPTIONS: { metric: AllocationMetric; label: string }[] = [
  { metric: 'currentValue', label: 'Valor atual' },
  { metric: 'costBasis', label: 'Investido' },
]

const activeMetric = ref<AllocationMetric>('currentValue')
const themeRevision = ref(0)

useChartThemeSync(() => {
  themeRevision.value += 1
})

const activeMetricLabel = computed(
  () => METRIC_OPTIONS.find((option) => option.metric === activeMetric.value)?.label ?? '',
)

const allocation = computed(() => computeAllocation(props.rows, activeMetric.value))

const coloredEntries = computed(() => {
  void themeRevision.value
  const individualCount = allocation.value.entries.filter((entry) => !entry.isOthers).length
  const shades = accentShades(resolveColor(WALLET_TYPES[props.kind].accent), individualCount)
  let shadeIndex = 0
  return allocation.value.entries.map((entry) => ({
    ...entry,
    color: entry.isOthers ? OTHERS_COLOR : shades[shadeIndex++],
  }))
})

const segments = computed(() =>
  coloredEntries.value.map((entry) => ({
    value: entry.value,
    label: entry.label,
    color: entry.color,
  })),
)

const exclusionNote = computed(() => {
  const count = allocation.value.excludedCount
  if (count === 0) return ''
  return count === 1
    ? '1 investimento sem preço atual ficou de fora deste gráfico.'
    : `${count} investimentos sem preço atual ficaram de fora deste gráfico.`
})
</script>

<template>
  <div class="allocation" data-testid="allocation-donut">
    <EmptyState
      v-if="rows.length === 0"
      icon="chart-donut"
      title="Nenhum investimento nesta carteira"
      text="A distribuição aparece assim que houver investimentos."
    />
    <template v-else>
      <div class="allocation-toggle" role="group" aria-label="Métrica da distribuição">
        <button
          v-for="option in METRIC_OPTIONS"
          :key="option.metric"
          type="button"
          class="allocation-toggle-option"
          :data-testid="`allocation-metric-${option.metric}`"
          :aria-pressed="activeMetric === option.metric"
          :class="{ 'is-active': activeMetric === option.metric }"
          @click="activeMetric = option.metric"
        >
          {{ option.label }}
        </button>
      </div>

      <template v-if="allocation.entries.length > 0">
        <DonutChart :segments="segments" :size="RING_SIZE" :thickness="RING_THICKNESS">
          <div class="allocation-center-label" data-testid="allocation-center-label">
            {{ activeMetricLabel }}
          </div>
          <div class="allocation-center-value" data-testid="allocation-center-value">
            {{ fmt.money(allocation.total, currency, { compact: true }) }}
          </div>
        </DonutChart>

        <ul class="allocation-legend" data-testid="allocation-legend">
          <li
            v-for="entry in coloredEntries"
            :key="entry.key"
            class="allocation-legend-row"
            data-testid="allocation-legend-entry"
          >
            <span class="allocation-legend-swatch" :style="{ background: entry.color }" />
            <span class="allocation-legend-label">{{ entry.label }}</span>
            <span class="allocation-legend-share">{{ fmt.pct(entry.share) }}</span>
            <span class="allocation-legend-value">{{ fmt.money(entry.value, currency) }}</span>
          </li>
        </ul>
      </template>
      <EmptyState
        v-else
        icon="chart-donut"
        title="Sem valores para exibir"
        text="Nenhum investimento tem valor para esta métrica."
      />

      <p v-if="exclusionNote" class="allocation-note" data-testid="allocation-exclusion-note">
        {{ exclusionNote }}
      </p>
    </template>
  </div>
</template>
