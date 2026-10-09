<script setup lang="ts">
import { computed } from 'vue'
import AreaChart from '@/components/charts/AreaChart.vue'
import Card from '@/components/ui/Card.vue'
import CardBody from '@/components/ui/CardBody.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import GainChip from '@/components/ui/GainChip.vue'
import { fmt } from '@/composables/useFormat'
import type { WalletDetail } from '@/types'

const props = defineProps<{ detail: WalletDetail }>()

const hasHistory = computed(() => props.detail.series.length > 0)

const chartSeries = computed(() => ({
  data: props.detail.series.map((point) => point.currentValue),
  labels: props.detail.series.map((point) => fmt.date(point.snapshotDate)),
}))

const deltas = computed(() => [
  { label: '1 dia', value: props.detail.dayChange },
  { label: '7 dias', value: props.detail.weekChange },
  { label: '30 dias', value: props.detail.monthChange },
])

function fmtY(value: number) {
  return fmt.money(value, props.detail.currency, { compact: true })
}
</script>

<template>
  <Card class="mb-0">
    <CardBody>
      <div
        class="is-flex is-flex-wrap-wrap is-align-items-flex-start is-justify-content-space-between is-gap-3 mb-4"
      >
        <div>
          <div class="chart-title">Desempenho</div>
          <div class="sub-caption">Valor atual ao longo do tempo</div>
        </div>
        <div
          v-if="hasHistory"
          class="is-flex is-flex-wrap-wrap is-align-items-center is-gap-5"
          data-testid="wallet-deltas"
        >
          <div
            v-for="delta in deltas"
            :key="delta.label"
            class="is-flex is-align-items-center is-gap-2"
          >
            <span class="is-size-7 has-text-grey">{{ delta.label }}</span>
            <GainChip :value="delta.value" :cur="detail.currency" compact />
          </div>
        </div>
      </div>

      <div v-if="hasHistory" class="chart-wrap">
        <AreaChart
          :data="chartSeries.data"
          :x-labels="chartSeries.labels"
          color="var(--primary)"
          :height="200"
          :fmt-y="fmtY"
        />
      </div>
      <EmptyState
        v-else
        icon="chart-line"
        title="Ainda sem histórico"
        text="O histórico diário começa a ser registrado a partir da primeira execução do job de snapshot."
      />
    </CardBody>
  </Card>
</template>
