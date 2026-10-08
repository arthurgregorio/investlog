<script setup lang="ts">
import { computed } from 'vue'
import Card from '@/components/ui/Card.vue'
import CardBody from '@/components/ui/CardBody.vue'
import AreaChart from '@/components/charts/AreaChart.vue'
import { fmt } from '@/composables/useFormat'
import type { SeriesPoint } from '@/types'

const props = defineProps<{ series: SeriesPoint[]; currency: string }>()

const chartSeries = computed(() => {
  if (!props.series.length) return { data: [0, 0], labels: ['', ''] }
  const data = props.series.map((point) => point.totalInvested)
  const labels = props.series.map((point) => fmt.dateShort(point.month + '-01'))
  if (data.length < 2) {
    data.unshift(0)
    labels.unshift('')
  }
  return { data, labels }
})

const latestInvested = computed(() => chartSeries.value.data[chartSeries.value.data.length - 1])

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
          <div class="sub-caption">Capital investido acumulado · {{ currency }}</div>
        </div>
        <div class="chart-big">
          {{ fmt.money(latestInvested, currency, { compact: true }) }}
        </div>
      </div>
      <div class="chart-wrap">
        <AreaChart
          :data="chartSeries.data"
          :x-labels="chartSeries.labels"
          color="var(--primary)"
          :height="252"
          :fmt-y="formatAxis"
        />
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
