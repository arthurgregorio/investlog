<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import Card from '@/components/ui/Card.vue'
import CardBody from '@/components/ui/CardBody.vue'
import SegmentedControl from '@/components/ui/SegmentedControl.vue'
import AllocationLegend from '@/components/charts/AllocationLegend.vue'
import DonutChart from '@/components/charts/DonutChart.vue'
import { overviewApi } from '@/api/overview'
import { fmt } from '@/composables/useFormat'
import { useStrategiesStore } from '@/stores/strategies'
import { chartColor } from '@/utils/allocation'
import type { AllocationGroup, AllocationGroupBy, OverviewAllocation } from '@/types'

const NEUTRAL_COLOR = 'var(--text-muted)'
const TYPELESS_LABEL = 'Sem tipo'

const props = defineProps<{ currency: string }>()

const GROUP_OPTIONS: { value: AllocationGroupBy; label: string; testId: string }[] = [
  { value: 'STRATEGY', label: 'Estratégia', testId: 'allocation-by-strategy' },
  { value: 'CLASS', label: 'Classe', testId: 'allocation-by-class' },
  { value: 'TYPE', label: 'Tipo', testId: 'allocation-by-type' },
]

const strategiesStore = useStrategiesStore()

const groupBy = ref<AllocationGroupBy>('STRATEGY')
const strategyId = ref('')
const allocation = ref<OverviewAllocation | null>(null)
const loading = ref(false)

const classStrategies = computed(() =>
  strategiesStore.strategies.filter((strategy) => !strategy.default),
)

function isNeutral(group: AllocationGroup): boolean {
  if (groupBy.value === 'STRATEGY') return group.id === strategiesStore.defaultStrategy?.id
  if (groupBy.value === 'TYPE') return group.label === TYPELESS_LABEL
  return false
}

const coloredGroups = computed(() => {
  let colorIndex = 0
  return (allocation.value?.groups ?? []).map((group) => ({
    ...group,
    color: isNeutral(group) ? NEUTRAL_COLOR : chartColor(colorIndex++),
  }))
})

const segments = computed(() =>
  coloredGroups.value.length
    ? coloredGroups.value.map((group) => ({
        value: group.value,
        color: group.color,
        label: group.label,
      }))
    : [{ value: 1, color: 'var(--chart-grid)', label: '—' }],
)

const legendEntries = computed(() =>
  coloredGroups.value.map((group) => ({
    key: group.id ?? group.label,
    label: group.label,
    color: group.color,
    share: fmt.pct(group.percentage),
    value: group.value,
  })),
)

const centerLabel = computed(() =>
  groupBy.value === 'CLASS'
    ? (strategiesStore.strategyById(strategyId.value)?.name ?? 'Classe')
    : 'Valor atual',
)

const currency = computed(() => allocation.value?.displayCurrency ?? props.currency)

async function load() {
  if (groupBy.value === 'CLASS' && !strategyId.value) {
    allocation.value = null
    return
  }
  loading.value = true
  try {
    allocation.value = await overviewApi.getAllocation(
      groupBy.value,
      groupBy.value === 'CLASS' ? strategyId.value : undefined,
    )
  } finally {
    loading.value = false
  }
}

watch(classStrategies, (strategies) => {
  if (!strategies.some((strategy) => strategy.id === strategyId.value)) {
    strategyId.value = strategies[0]?.id ?? ''
  }
})

watch([groupBy, strategyId], load)

onMounted(async () => {
  await strategiesStore.load()
  strategyId.value = classStrategies.value[0]?.id ?? ''
  await load()
})
</script>

<template>
  <Card class="alloc-card">
    <CardBody>
      <div
        class="is-flex is-flex-wrap-wrap is-align-items-center is-justify-content-space-between is-gap-2 mb-3"
      >
        <div class="chart-title">Alocação</div>
        <SegmentedControl v-model="groupBy" :options="GROUP_OPTIONS" group-label="Agrupar por" />
      </div>
      <div v-if="groupBy === 'CLASS'" class="control mb-3">
        <b-select
          v-model="strategyId"
          size="is-small"
          expanded
          :disabled="classStrategies.length === 0"
          data-testid="allocation-strategy"
        >
          <option v-for="strategy in classStrategies" :key="strategy.id" :value="strategy.id">
            {{ strategy.name }}
          </option>
        </b-select>
      </div>
      <p
        v-if="groupBy === 'CLASS' && classStrategies.length === 0"
        class="is-size-7 has-text-grey"
        data-testid="allocation-no-strategies"
      >
        Crie uma estratégia para ver a alocação por classe.
      </p>
      <div v-else class="alloc-body is-relative">
        <b-loading :is-full-page="false" :model-value="loading" />
        <div class="alloc-donut">
          <DonutChart :segments="segments" :size="200" :thickness="28">
            <div class="donut-center-label">{{ centerLabel }}</div>
            <div class="donut-center-value">
              {{ fmt.money(allocation?.totalValue ?? 0, currency, { compact: true }) }}
            </div>
          </DonutChart>
        </div>
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
  gap: 18px;
}

.alloc-donut {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
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
