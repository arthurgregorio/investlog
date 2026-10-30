<script setup lang="ts">
import { computed, ref } from 'vue'
import Card from '@/components/ui/Card.vue'
import SegmentedControl from '@/components/ui/SegmentedControl.vue'
import DonutChart from '@/components/charts/DonutChart.vue'
import AllocationLegend from '@/components/charts/AllocationLegend.vue'
import ClassHoldingIdentity from '@/components/strategies/ClassHoldingIdentity.vue'
import { fmt } from '@/composables/useFormat'
import { chartColor } from '@/utils/allocation'
import {
  driftLabel,
  driftTagType,
  holdingCountLabel,
  moneyToTarget,
  targetLabel,
} from '@/utils/allocationLabels'
import type { ClassAllocation } from '@/types'

type Lens = 'holding' | 'segment'

const SEGMENTLESS = 'Sem segmento'
const RING_SIZE = 236
const RING_THICKNESS = 38
const SEGMENT_SPACING = 2

const props = defineProps<{ strategyClass: ClassAllocation; currency: string }>()

const LENS_OPTIONS: { value: Lens; label: string; testId: string }[] = [
  { value: 'holding', label: 'Por ativo', testId: 'class-lens-holding' },
  { value: 'segment', label: 'Por segmento', testId: 'class-lens-segment' },
]

const lens = ref<Lens>('holding')

const coloredSegments = computed(() => {
  const largestShare = Math.max(...props.strategyClass.segments.map((segment) => segment.value), 0)
  return props.strategyClass.segments.map((segment, index) => ({
    ...segment,
    color: segment.label === SEGMENTLESS ? 'var(--text-muted)' : chartColor(index),
    holdingCount: props.strategyClass.holdings.filter(
      (holding) => (holding.segmentLabel ?? SEGMENTLESS) === segment.label,
    ).length,
    relativeShare: largestShare ? (segment.value / largestShare) * 100 : 0,
  }))
})

const donutSegments = computed(() =>
  coloredSegments.value.map((segment) => ({
    value: segment.value,
    label: segment.label,
    color: segment.color,
  })),
)

const legendEntries = computed(() =>
  coloredSegments.value.map((segment) => ({
    key: segment.label,
    label: segment.label,
    name: holdingCountLabel(segment.holdingCount),
    color: segment.color,
    share: fmt.pct(segment.classPercentage),
    value: segment.value,
    relativeShare: segment.relativeShare,
  })),
)

const caption = computed(() =>
  lens.value === 'holding'
    ? `Meta e participação de cada investimento na classe · meta da classe ${targetLabel(props.strategyClass.targetPercentage)}`
    : 'Participação de cada segmento na classe · só leitura, segmentos não têm meta',
)
</script>

<template>
  <Card class="table-card mb-0" data-testid="class-allocation-detail">
    <div class="card-body">
      <div
        class="is-flex is-flex-wrap-wrap is-align-items-flex-start is-justify-content-space-between is-gap-3"
      >
        <div>
          <div class="chart-title">{{ strategyClass.name }}</div>
          <div class="sub-caption">{{ caption }}</div>
        </div>
        <SegmentedControl v-model="lens" :options="LENS_OPTIONS" group-label="Ver a classe" />
      </div>
    </div>

    <template v-if="lens === 'holding'">
      <p
        v-if="strategyClass.holdings.length === 0"
        class="card-body has-text-grey"
        data-testid="class-allocation-empty"
      >
        Nenhum investimento nesta classe ainda.
      </p>
      <div v-else class="table-wrap">
        <div class="table-scroll">
          <table class="inv-table">
            <thead>
              <tr>
                <th>Ativo</th>
                <th>Carteira · tipo</th>
                <th class="c-num has-text-right">Meta na classe</th>
                <th class="c-num has-text-right">Atual na classe</th>
                <th class="c-num has-text-right">Desvio</th>
                <th class="c-num has-text-right">Para a meta</th>
                <th class="c-num has-text-right">Valor atual</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="holding in strategyClass.holdings"
                :key="holding.id"
                class="inv-row"
                :class="{ 'is-frozen': holding.frozen }"
                data-testid="allocation-holding-row"
              >
                <ClassHoldingIdentity :holding="holding" />
                <td class="c-num has-text-right">
                  <template v-if="holding.typed">{{ fmt.pct(holding.targetPercentage) }}</template>
                  <template v-else>
                    <span class="has-text-grey">{{ fmt.pct(holding.targetPercentage) }}</span>
                    <div class="is-size-7 has-text-grey">do restante</div>
                  </template>
                </td>
                <td class="c-num has-text-right has-text-weight-semibold">
                  {{ fmt.pct(holding.actualPercentage) }}
                </td>
                <td class="c-num has-text-right">
                  <span class="tag" :class="driftTagType(holding.driftPercentagePoints)">
                    {{ driftLabel(holding.driftPercentagePoints) }}
                  </span>
                </td>
                <td class="c-num has-text-right">
                  {{ moneyToTarget(holding.driftValue, currency) }}
                </td>
                <td class="c-num has-text-right has-text-weight-bold">
                  {{ fmt.money(holding.value, currency) }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>

    <div v-else class="card-body" data-testid="class-segments">
      <p v-if="strategyClass.segments.length === 0" class="has-text-grey">
        Nenhum investimento nesta classe ainda.
      </p>
      <div
        v-else
        class="is-flex is-flex-wrap-wrap is-align-items-center is-justify-content-center is-gap-6"
      >
        <DonutChart
          :segments="donutSegments"
          :size="RING_SIZE"
          :thickness="RING_THICKNESS"
          :spacing="SEGMENT_SPACING"
        >
          <div class="is-size-7 has-text-grey">Valor atual</div>
          <div class="is-size-5 has-text-weight-bold mt-1">
            {{ fmt.money(strategyClass.value, currency, { compact: true }) }}
          </div>
          <div class="is-size-7 has-text-grey mt-1">
            {{ holdingCountLabel(strategyClass.holdings.length, 'ativo') }}
          </div>
        </DonutChart>
        <AllocationLegend :entries="legendEntries" :currency="currency" detailed />
      </div>
    </div>
  </Card>
</template>
