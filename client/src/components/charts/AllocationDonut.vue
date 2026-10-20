<script setup lang="ts">
import { computed, ref } from 'vue'
import AllocationLegend from '@/components/charts/AllocationLegend.vue'
import DonutChart from '@/components/charts/DonutChart.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import SegmentedControl from '@/components/ui/SegmentedControl.vue'
import { fmt } from '@/composables/useFormat'
import {
  chartColor,
  computeAllocation,
  OTHERS_SHARE_CUTOFF,
  SEGMENTLESS_KEY,
  TOP_POSITIONS_COUNT,
  TYPELESS_KEY,
  type AllocationGrouping,
  type AllocationMetric,
} from '@/utils/allocation'
import type { HoldingRow } from '@/types'

const RING_SIZE = 236
const RING_THICKNESS = 38
const SEGMENT_SPACING = 2
const OTHERS_COLOR = 'var(--text-muted)'
const NEUTRAL_KEYS = [SEGMENTLESS_KEY, TYPELESS_KEY]

const props = withDefaults(
  defineProps<{ rows: HoldingRow[]; currency: string; groupings?: AllocationGrouping[] }>(),
  { groupings: () => ['holding'] },
)

const METRIC_OPTIONS: { value: AllocationMetric; label: string; testId: string }[] = [
  { value: 'currentValue', label: 'Valor atual', testId: 'allocation-metric-currentValue' },
  { value: 'costBasis', label: 'Investido', testId: 'allocation-metric-costBasis' },
]

const GROUPING_OPTIONS: { value: AllocationGrouping; label: string; testId: string }[] = [
  { value: 'holding', label: 'Por ativo', testId: 'allocation-grouping-holding' },
  { value: 'type', label: 'Por tipo', testId: 'allocation-grouping-type' },
  { value: 'segment', label: 'Por segmento', testId: 'allocation-grouping-segment' },
]

const GROUPED_NOUNS: Record<AllocationGrouping, { singular: string; plural: string }> = {
  holding: { singular: 'investimento', plural: 'investimentos' },
  type: { singular: 'tipo', plural: 'tipos' },
  segment: { singular: 'segmento', plural: 'segmentos' },
}

const activeMetric = ref<AllocationMetric>('currentValue')
const selectedGrouping = ref<AllocationGrouping>('holding')

const groupingOptions = computed(() =>
  GROUPING_OPTIONS.filter((option) => props.groupings.includes(option.value)),
)

const activeGrouping = computed<AllocationGrouping>({
  get: () =>
    props.groupings.includes(selectedGrouping.value) ? selectedGrouping.value : 'holding',
  set: (grouping) => {
    selectedGrouping.value = grouping
  },
})

const groupedNoun = computed(() => GROUPED_NOUNS[activeGrouping.value])

const activeMetricLabel = computed(
  () => METRIC_OPTIONS.find((option) => option.value === activeMetric.value)?.label ?? '',
)

const allocation = computed(() =>
  computeAllocation(props.rows, activeMetric.value, OTHERS_SHARE_CUTOFF, activeGrouping.value),
)

const coloredEntries = computed(() => {
  const entries = allocation.value.entries
  const largestShare = Math.max(...entries.map((entry) => entry.share))
  return entries.map((entry, index) => ({
    ...entry,
    color: entry.isOthers || NEUTRAL_KEYS.includes(entry.key) ? OTHERS_COLOR : chartColor(index),
    relativeShare: (entry.share / largestShare) * 100,
  }))
})

const segments = computed(() =>
  coloredEntries.value.map((entry) => ({
    value: entry.value,
    label: entry.label,
    color: entry.color,
  })),
)

const legendEntries = computed(() =>
  coloredEntries.value.map((entry) => ({
    key: entry.key,
    label: entry.label,
    name: entry.name,
    color: entry.color,
    share: fmt.pct(entry.share),
    value: entry.value,
    relativeShare: entry.relativeShare,
  })),
)

const assetCount = computed(() =>
  props.rows.length === 1 ? '1 ativo' : `${props.rows.length} ativos`,
)

const concentrationNote = computed(() => {
  const notes: string[] = []
  const topThreeShare = allocation.value.topThreeShare
  if (topThreeShare != null) {
    notes.push(
      `As ${TOP_POSITIONS_COUNT} maiores posições concentram ${fmt.pct(topThreeShare)} do total.`,
    )
  }
  if (allocation.value.entries.some((entry) => entry.isOthers)) {
    notes.push(`“Outros” reúne os ${groupedNoun.value.plural} abaixo de ${OTHERS_SHARE_CUTOFF}%.`)
  }
  return notes.join(' ')
})

const exclusionNote = computed(() => {
  const count = allocation.value.excludedCount
  if (count === 0) return ''
  return count === 1
    ? '1 investimento sem preço atual ficou de fora deste gráfico.'
    : `${count} investimentos sem preço atual ficaram de fora deste gráfico.`
})
</script>

<template>
  <div data-testid="allocation-donut">
    <div
      class="is-flex is-flex-wrap-wrap is-align-items-flex-start is-justify-content-space-between is-gap-3 mb-5"
    >
      <div>
        <div class="chart-title">Distribuição</div>
        <div class="sub-caption">Participação de cada {{ groupedNoun.singular }} na carteira</div>
      </div>
      <div v-if="rows.length > 0" class="is-flex is-flex-wrap-wrap is-gap-2">
        <SegmentedControl
          v-if="groupingOptions.length > 1"
          v-model="activeGrouping"
          :options="groupingOptions"
          group-label="Agrupar por"
        />
        <SegmentedControl
          v-model="activeMetric"
          :options="METRIC_OPTIONS"
          group-label="Métrica da distribuição"
        />
      </div>
    </div>

    <EmptyState
      v-if="rows.length === 0"
      icon="chart-donut"
      title="Nenhum investimento nesta carteira"
      text="A distribuição aparece assim que houver investimentos."
    />
    <template v-else>
      <div
        v-if="allocation.entries.length > 0"
        class="is-flex is-flex-wrap-wrap is-align-items-center is-justify-content-center is-gap-6"
      >
        <DonutChart
          :segments="segments"
          :size="RING_SIZE"
          :thickness="RING_THICKNESS"
          :spacing="SEGMENT_SPACING"
        >
          <div class="is-size-7 has-text-grey" data-testid="allocation-center-label">
            {{ activeMetricLabel }}
          </div>
          <div class="is-size-5 has-text-weight-bold mt-1" data-testid="allocation-center-value">
            {{ fmt.money(allocation.total, currency, { compact: true }) }}
          </div>
          <div class="is-size-7 has-text-grey mt-1">{{ assetCount }}</div>
        </DonutChart>

        <div class="allocation-summary">
          <AllocationLegend :entries="legendEntries" :currency="currency" detailed />

          <p
            v-if="concentrationNote"
            class="is-size-7 has-text-grey px-1 mt-4"
            data-testid="allocation-concentration-note"
          >
            {{ concentrationNote }}
          </p>
          <p
            v-if="exclusionNote"
            class="is-size-7 has-text-grey px-1 mt-2"
            data-testid="allocation-exclusion-note"
          >
            {{ exclusionNote }}
          </p>
        </div>
      </div>
      <template v-else>
        <EmptyState
          icon="chart-donut"
          title="Sem valores para exibir"
          text="Nenhum investimento tem valor para esta métrica."
        />
        <p
          v-if="exclusionNote"
          class="is-size-7 has-text-grey mt-2"
          data-testid="allocation-exclusion-note"
        >
          {{ exclusionNote }}
        </p>
      </template>
    </template>
  </div>
</template>

<style scoped>
.allocation-summary {
  flex: 1 1 480px;
  min-width: 0;
}
</style>
