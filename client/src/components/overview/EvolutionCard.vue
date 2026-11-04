<script setup lang="ts">
import { computed } from 'vue'
import Card from '@/components/ui/Card.vue'
import CardBody from '@/components/ui/CardBody.vue'
import AllocationLegend from '@/components/charts/AllocationLegend.vue'
import PatrimonyChart from '@/components/charts/PatrimonyChart.vue'
import { fmt } from '@/composables/useFormat'
import type { SeriesPoint } from '@/types'

const props = defineProps<{ series: SeriesPoint[]; currency: string }>()

const chartSeries = computed(() => {
  const points = props.series.length
    ? props.series
    : [{ month: '', ownMoney: 0, result: 0, marketValued: false }]
  const ownMoney = points.map((point) => point.ownMoney)
  const result = points.map((point) => point.result)
  const labels = points.map((point) => (point.month ? fmt.dateShort(point.month + '-01') : ''))
  const firstMarketValued = points.findIndex((point) => point.marketValued)
  if (ownMoney.length < 2) {
    ownMoney.unshift(0)
    result.unshift(0)
    labels.unshift('')
  }
  const offset = ownMoney.length - points.length
  return {
    ownMoney,
    result,
    labels,
    markerIndex: firstMarketValued < 0 ? null : firstMarketValued + offset,
  }
})

const latestIndex = computed(() => chartSeries.value.ownMoney.length - 1)
const latestOwnMoney = computed(() => chartSeries.value.ownMoney[latestIndex.value] ?? 0)
const latestResult = computed(() => chartSeries.value.result[latestIndex.value] ?? 0)
const latestTotal = computed(() => latestOwnMoney.value + latestResult.value)
const resultIsPositive = computed(() => latestResult.value >= 0)

function shareOf(value: number): string {
  return latestTotal.value ? fmt.pct((value / latestTotal.value) * 100) : '0%'
}

const legendEntries = computed(() => [
  {
    key: 'own-money',
    label: 'Capital próprio',
    hint: 'Dinheiro novo que você colocou nas carteiras, menos o que sacou para o banco.',
    color: 'var(--primary)',
    share: shareOf(latestOwnMoney.value),
    value: latestOwnMoney.value,
  },
  {
    key: 'result',
    label: resultIsPositive.value ? 'Resultado total' : 'Perda total',
    hint: 'Lucro já realizado mais a variação do que você tem hoje. Antes dos registros diários, só o lucro realizado.',
    color: resultIsPositive.value ? 'var(--up)' : 'var(--down)',
    share: shareOf(latestResult.value),
    value: latestResult.value,
  },
])

function formatAxis(value: number) {
  return (
    fmt.sym(props.currency) +
    ' ' +
    (Math.abs(value) >= 1000 ? (value / 1000).toFixed(0) + 'k' : value.toFixed(0))
  )
}
</script>

<template>
  <Card class="chart-card">
    <CardBody>
      <div
        class="is-flex is-align-items-flex-start is-justify-content-space-between is-gap-1.5 mb-3"
      >
        <div>
          <div class="chart-title">Patrimônio mês a mês</div>
          <div class="sub-caption">Capital próprio + resultado total · {{ currency }}</div>
        </div>
        <div class="chart-big">
          {{ fmt.money(latestTotal, currency, { compact: true }) }}
        </div>
      </div>
      <div class="chart-wrap is-flex is-flex-direction-column is-flex-grow-1">
        <PatrimonyChart
          :own-money="chartSeries.ownMoney"
          :result="chartSeries.result"
          :labels="chartSeries.labels"
          :marker-index="chartSeries.markerIndex"
          marker-label="início dos registros diários"
          :fmt-y="formatAxis"
        />
      </div>
      <div class="mt-4">
        <AllocationLegend :entries="legendEntries" :currency="currency" />
      </div>
    </CardBody>
  </Card>
</template>

<style scoped>
.chart-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.chart-card > :deep(.card-body) {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.chart-big {
  font-size: 18px;
  font-weight: 800;
  letter-spacing: -0.02em;
}
</style>
