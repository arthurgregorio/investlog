<script setup lang="ts">
import { computed, ref } from 'vue'
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
  type AllocationGrouping,
  type AllocationMetric,
} from '@/utils/allocation'
import type { HoldingRow } from '@/types'

const RING_SIZE = 236
const RING_THICKNESS = 38
const SEGMENT_SPACING = 2
const OTHERS_COLOR = 'var(--text-muted)'

const props = defineProps<{ rows: HoldingRow[]; currency: string; groupable?: boolean }>()

const METRIC_OPTIONS: { value: AllocationMetric; label: string; testId: string }[] = [
  { value: 'currentValue', label: 'Valor atual', testId: 'allocation-metric-currentValue' },
  { value: 'costBasis', label: 'Investido', testId: 'allocation-metric-costBasis' },
]

const GROUPING_OPTIONS: { value: AllocationGrouping; label: string; testId: string }[] = [
  { value: 'holding', label: 'Por ativo', testId: 'allocation-grouping-holding' },
  { value: 'segment', label: 'Por segmento', testId: 'allocation-grouping-segment' },
]

const activeMetric = ref<AllocationMetric>('currentValue')
const activeGrouping = ref<AllocationGrouping>('holding')

const groupingBySegment = computed(() => props.groupable && activeGrouping.value === 'segment')

const activeMetricLabel = computed(
  () => METRIC_OPTIONS.find((option) => option.value === activeMetric.value)?.label ?? '',
)

const allocation = computed(() =>
  computeAllocation(
    props.rows,
    activeMetric.value,
    OTHERS_SHARE_CUTOFF,
    groupingBySegment.value ? 'segment' : 'holding',
  ),
)

const coloredEntries = computed(() => {
  const entries = allocation.value.entries
  const largestShare = Math.max(...entries.map((entry) => entry.share))
  return entries.map((entry, index) => ({
    ...entry,
    color: entry.isOthers || entry.key === SEGMENTLESS_KEY ? OTHERS_COLOR : chartColor(index),
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
    const grouped = groupingBySegment.value ? 'os segmentos' : 'os investimentos'
    notes.push(`“Outros” reúne ${grouped} abaixo de ${OTHERS_SHARE_CUTOFF}%.`)
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
        <div class="sub-caption">
          Participação de cada {{ groupingBySegment ? 'segmento' : 'investimento' }} na carteira
        </div>
      </div>
      <div v-if="rows.length > 0" class="is-flex is-flex-wrap-wrap is-gap-2">
        <SegmentedControl
          v-if="groupable"
          v-model="activeGrouping"
          :options="GROUPING_OPTIONS"
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
          <ul class="allocation-legend" data-testid="allocation-legend">
            <li
              v-for="entry in coloredEntries"
              :key="entry.key"
              class="allocation-legend-row"
              data-testid="allocation-legend-entry"
            >
              <span class="allocation-legend-swatch" :style="{ background: entry.color }" />
              <span class="allocation-legend-name">
                <span class="has-text-weight-semibold">{{ entry.label }}</span>
                <span v-if="entry.name" class="is-size-7 has-text-grey ml-2">{{ entry.name }}</span>
              </span>
              <progress
                class="progress mb-0"
                max="100"
                :value="entry.relativeShare"
                :style="{ '--bulma-progress-value-background-color': entry.color }"
              />
              <span class="has-text-grey has-text-right">{{ fmt.pct(entry.share) }}</span>
              <span class="has-text-right">{{ fmt.money(entry.value, currency) }}</span>
            </li>
          </ul>

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

.allocation-legend {
  margin: 0;
  padding: 0;
  list-style: none;
}

.allocation-legend-row {
  display: grid;
  grid-template-columns: 12px minmax(0, 1.1fr) minmax(60px, 1fr) 64px 110px;
  align-items: center;
  gap: 12px;
  min-height: 38px;
  padding: 0 4px;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  border-bottom: 1px solid var(--bulma-border-weak);
}

.allocation-legend-row:last-child {
  border-bottom: none;
}

.allocation-legend-swatch {
  width: 12px;
  height: 12px;
  border-radius: 3px;
}

.allocation-legend-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.allocation-legend-row .progress {
  height: 6px;
}
</style>
