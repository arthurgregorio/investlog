<script setup lang="ts">
import { computed } from 'vue'
import Card from '@/components/ui/Card.vue'
import CardBody from '@/components/ui/CardBody.vue'
import AllocationLegend from '@/components/charts/AllocationLegend.vue'
import AreaChart from '@/components/charts/AreaChart.vue'
import { fmt } from '@/composables/useFormat'
import type { SeriesPoint } from '@/types'

const OWN_MONEY_COLOR = 'var(--primary)'
const REINVESTED_PROFIT_COLOR = 'var(--chart-6)'

const props = defineProps<{ series: SeriesPoint[]; currency: string }>()

const chartSeries = computed(() => {
  if (!props.series.length) return { ownMoney: [0, 0], reinvestedProfit: [0, 0], labels: ['', ''] }
  const ownMoney = props.series.map((point) => point.ownMoney)
  const reinvestedProfit = props.series.map((point) => point.reinvestedProfit)
  const labels = props.series.map((point) => fmt.dateShort(point.month + '-01'))
  if (ownMoney.length < 2) {
    ownMoney.unshift(0)
    reinvestedProfit.unshift(0)
    labels.unshift('')
  }
  return { ownMoney, reinvestedProfit, labels }
})

const latestIndex = computed(() => chartSeries.value.ownMoney.length - 1)
const latestOwnMoney = computed(() => chartSeries.value.ownMoney[latestIndex.value]!)
const latestReinvestedProfit = computed(
  () => chartSeries.value.reinvestedProfit[latestIndex.value]!,
)
const latestTotal = computed(() => latestOwnMoney.value + latestReinvestedProfit.value)

function shareOf(value: number): string {
  return latestTotal.value ? fmt.pct((value / latestTotal.value) * 100) : '0%'
}

const legendEntries = computed(() => [
  {
    key: 'own-money',
    label: 'Capital próprio',
    hint: 'Dinheiro novo que você colocou nas carteiras, menos o que sacou para o banco.',
    color: OWN_MONEY_COLOR,
    share: shareOf(latestOwnMoney.value),
    value: latestOwnMoney.value,
  },
  {
    key: 'reinvested-profit',
    label: 'Lucro reinvestido',
    hint: 'Lucro de resgates que voltou para compras em vez de sair do InvestLog.',
    color: REINVESTED_PROFIT_COLOR,
    share: shareOf(latestReinvestedProfit.value),
    value: latestReinvestedProfit.value,
  },
])

function formatAxis(value: number) {
  return (
    fmt.sym(props.currency) +
    ' ' +
    (value >= 1000 ? (value / 1000).toFixed(0) + 'k' : value.toFixed(0))
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
          <div class="chart-title">Evolução dos aportes</div>
          <div class="sub-caption">Capital próprio + lucro reinvestido · {{ currency }}</div>
        </div>
        <div class="chart-big">
          {{ fmt.money(latestTotal, currency, { compact: true }) }}
        </div>
      </div>
      <div class="chart-wrap">
        <AreaChart
          :data="chartSeries.ownMoney"
          :stacked-data="chartSeries.reinvestedProfit"
          :x-labels="chartSeries.labels"
          :color="OWN_MONEY_COLOR"
          :stacked-color="REINVESTED_PROFIT_COLOR"
          :height="252"
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
