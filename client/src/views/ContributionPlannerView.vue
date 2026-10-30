<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import Card from '@/components/ui/Card.vue'
import NumberInput from '@/components/ui/NumberInput.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import PlanHoldingsTable from '@/components/strategies/PlanHoldingsTable.vue'
import { strategiesApi } from '@/api/strategies'
import { fmt } from '@/composables/useFormat'
import { useStrategiesStore } from '@/stores/strategies'
import { problemDetailMessage } from '@/utils/apiErrors'
import { chartColor } from '@/utils/allocation'
import { holdingCountLabel, targetLabel, type PlanHoldingRow } from '@/utils/allocationLabels'
import type { ContributionPlan, HoldingAllocation, StrategyAllocation } from '@/types'

const DEBOUNCE_MS = 300

const props = defineProps<{ id: string }>()

const router = useRouter()
const strategiesStore = useStrategiesStore()

const amount = ref<number | ''>('')
const allocation = ref<StrategyAllocation | null>(null)
const plan = ref<ContributionPlan | null>(null)
const planError = ref<string | null>(null)
const planning = ref(false)
const activeTab = ref(0)
let debounceTimer: ReturnType<typeof setTimeout> | undefined

const strategies = computed(() =>
  strategiesStore.strategies.filter((strategy) => !strategy.default),
)
const strategyName = computed(
  () => strategiesStore.strategyById(props.id)?.name ?? allocation.value?.strategyName ?? '',
)
const currency = computed(() => allocation.value?.displayCurrency ?? 'BRL')

const holdingsById = computed(() => {
  const entries = new Map<string, { holding: HoldingAllocation; className: string }>()
  allocation.value?.classes.forEach((strategyClass) =>
    strategyClass.holdings.forEach((holding) =>
      entries.set(holding.id, { holding, className: strategyClass.name }),
    ),
  )
  return entries
})

const holdingCount = computed(() => holdingsById.value.size)

const suggested = computed<PlanHoldingRow[]>(() =>
  (plan.value?.classes ?? []).flatMap((strategyClass) =>
    strategyClass.holdings.flatMap((holding) => {
      const entry = holdingsById.value.get(holding.id)
      return entry
        ? [
            {
              id: holding.id,
              className: strategyClass.name,
              holding: entry.holding,
              amount: holding.amount,
            },
          ]
        : []
    }),
  ),
)

const ignored = computed<PlanHoldingRow[]>(() =>
  (plan.value?.leftOut ?? []).flatMap((leftOut) => {
    const entry = holdingsById.value.get(leftOut.id)
    return entry
      ? [
          {
            id: leftOut.id,
            className: leftOut.className,
            holding: entry.holding,
            reason: leftOut.reason,
          },
        ]
      : []
  }),
)

const distributed = computed(() =>
  (plan.value?.classes ?? []).reduce((total, strategyClass) => total + strategyClass.amount, 0),
)

const classRows = computed(() => {
  const totalAfter = plan.value?.totalValueAfter ?? 0
  return (allocation.value?.classes ?? []).map((strategyClass) => {
    const planned = plan.value?.classes.find((receiving) => receiving.id === strategyClass.id)
    const aboveTarget = (plan.value?.leftOut ?? []).some(
      (leftOut) =>
        leftOut.reason === 'CLASS_ABOVE_TARGET' && leftOut.className === strategyClass.name,
    )
    return {
      ...strategyClass,
      planned,
      aboveTarget,
      percentageAfter:
        planned?.percentageAfter ?? (totalAfter ? (strategyClass.value / totalAfter) * 100 : null),
    }
  })
})

const headline = computed(() => {
  if (!strategyName.value) return ''
  const value = Number(amount.value)
  return value > 0
    ? `${strategyName.value} · sugestão para um aporte de ${fmt.money(value, currency.value)}`
    : strategyName.value
})

async function loadAllocation() {
  allocation.value = null
  allocation.value = await strategiesApi.findAllocation(props.id)
}

async function loadPlan() {
  const value = Number(amount.value)
  planError.value = null
  if (!(value > 0)) {
    plan.value = null
    return
  }
  planning.value = true
  try {
    plan.value = await strategiesApi.planContribution(props.id, value)
  } catch (error) {
    plan.value = null
    planError.value = problemDetailMessage(error) ?? 'Não foi possível calcular a sugestão.'
  } finally {
    planning.value = false
  }
}

function schedulePlan() {
  clearTimeout(debounceTimer)
  debounceTimer = setTimeout(loadPlan, DEBOUNCE_MS)
}

watch(amount, schedulePlan)

watch(
  () => props.id,
  async () => {
    await loadAllocation()
    await loadPlan()
  },
)

onMounted(async () => {
  await Promise.all([strategiesStore.load(), loadAllocation()])
})

onBeforeUnmount(() => clearTimeout(debounceTimer))

function changeStrategy(strategyId: string) {
  router.replace({ name: 'strategy-plan', params: { id: strategyId } })
}

function goBack() {
  router.push({ name: 'strategies', query: { strategy: props.id } })
}
</script>

<template>
  <div class="page">
    <div>
      <b-button type="is-ghost" icon-left="arrow-left" size="is-small" @click="goBack">
        Estratégias
      </b-button>
    </div>
    <PageHeader title="Planejar aporte" :description="headline" />

    <div class="investments-body">
      <aside class="card filter-rail">
        <div class="is-flex is-justify-content-space-between is-align-items-center px-4 py-3">
          <h2 class="is-flex is-align-items-center is-gap-1 has-text-weight-bold">Aporte</h2>
        </div>
        <div class="px-4 pb-4 is-flex is-flex-direction-column is-gap-3">
          <b-field label="Estratégia">
            <b-select
              :model-value="id"
              expanded
              data-testid="plan-strategy"
              @update:model-value="changeStrategy"
            >
              <option v-for="strategy in strategies" :key="strategy.id" :value="strategy.id">
                {{ strategy.name }}
              </option>
            </b-select>
          </b-field>
          <b-field label="Valor do aporte">
            <NumberInput
              v-model="amount"
              :prefix="fmt.sym(currency)"
              placeholder="0,00"
              min="0"
              data-testid="plan-amount"
            />
          </b-field>
          <div v-if="plan" class="notification is-size-7 mb-0" data-testid="plan-summary">
            <div class="is-flex is-justify-content-space-between">
              <span>Aporte</span><strong>{{ fmt.money(plan.amount, currency) }}</strong>
            </div>
            <div class="is-flex is-justify-content-space-between mt-1">
              <span>Distribuído</span>
              <strong class="has-text-success">{{ fmt.money(distributed, currency) }}</strong>
            </div>
            <div class="is-flex is-justify-content-space-between mt-1">
              <span>Classes</span>
              <strong>{{ plan.classes.length }} de {{ allocation?.classes.length ?? 0 }}</strong>
            </div>
            <div class="is-flex is-justify-content-space-between mt-1">
              <span>Investimentos</span>
              <strong>{{ suggested.length }} de {{ holdingCount }}</strong>
            </div>
          </div>
          <p class="is-size-7 has-text-grey">
            Só recebe aporte o que está abaixo da meta: primeiro entre as classes, depois entre os
            investimentos da classe, na proporção do que falta para cada um. Congelados nunca
            recebem, e Sem estratégia fica de fora. Nada é registrado.
          </p>
        </div>
      </aside>

      <div class="investments-main is-flex is-flex-direction-column is-gap-4">
        <b-message
          v-if="planError"
          type="is-warning"
          has-icon
          class="mb-0"
          data-testid="plan-error"
        >
          {{ planError }}
        </b-message>

        <Card class="table-card mb-0 is-relative">
          <b-loading :is-full-page="false" :model-value="!allocation || planning" />
          <div class="card-body">
            <div class="chart-title">Por classe</div>
            <div class="sub-caption">Participação de cada classe antes e depois do aporte</div>
          </div>
          <div class="table-wrap">
            <div class="table-scroll">
              <table class="inv-table">
                <thead>
                  <tr>
                    <th>Classe</th>
                    <th class="c-num has-text-right">Meta</th>
                    <th class="c-num has-text-right">Atual</th>
                    <th class="c-num has-text-right">Depois do aporte</th>
                    <th class="c-num has-text-right">Aporte sugerido</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="(strategyClass, index) in classRows"
                    :key="strategyClass.id"
                    class="inv-row"
                    data-testid="plan-class-row"
                  >
                    <td>
                      <div class="is-flex is-align-items-center is-gap-1.5">
                        <span class="wref-dot" :style="{ background: chartColor(index) }" />
                        <div class="name-meta">
                          <div class="t-ticker has-text-weight-bold">{{ strategyClass.name }}</div>
                          <div class="t-name">
                            {{ holdingCountLabel(strategyClass.holdings.length) }}
                          </div>
                        </div>
                      </div>
                    </td>
                    <td class="c-num has-text-right">
                      {{ targetLabel(strategyClass.targetPercentage) }}
                    </td>
                    <td class="c-num has-text-right">
                      {{ fmt.pct(strategyClass.actualPercentage) }}
                    </td>
                    <td class="c-num has-text-right has-text-weight-semibold">
                      {{
                        strategyClass.percentageAfter == null
                          ? '—'
                          : fmt.pct(strategyClass.percentageAfter)
                      }}
                    </td>
                    <td class="c-num has-text-right">
                      <span v-if="strategyClass.planned" class="has-text-weight-bold">
                        {{ fmt.money(strategyClass.planned.amount, currency) }}
                      </span>
                      <span v-else-if="strategyClass.aboveTarget" class="tag is-warning is-light">
                        acima da meta
                      </span>
                      <span v-else-if="plan" class="tag is-light">nenhum investimento recebe</span>
                      <span v-else class="has-text-grey">—</span>
                    </td>
                  </tr>
                  <tr class="inv-row" data-testid="plan-total-row">
                    <td><span class="has-text-weight-bold">Total</span></td>
                    <td class="c-num has-text-right">100%</td>
                    <td class="c-num has-text-right">100%</td>
                    <td class="c-num has-text-right has-text-weight-semibold">100%</td>
                    <td class="c-num has-text-right has-text-weight-bold">
                      {{ plan ? fmt.money(distributed, currency) : '—' }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </Card>

        <Card class="table-card mb-0">
          <b-tabs v-model="activeTab" class="wd-tabs" :animated="false">
            <b-tab-item>
              <template #header>
                <span>Sugeridos</span>
                <span class="tag is-rounded ml-2">{{ suggested.length }}</span>
              </template>
              <PlanHoldingsTable
                :rows="suggested"
                :currency="currency"
                empty-text="Informe o valor do aporte para ver a sugestão."
              />
            </b-tab-item>
            <b-tab-item>
              <template #header>
                <span>Ignorados</span>
                <span class="tag is-rounded ml-2">{{ ignored.length }}</span>
              </template>
              <PlanHoldingsTable
                :rows="ignored"
                :currency="currency"
                empty-text="Nenhum investimento ficou de fora."
              />
            </b-tab-item>
          </b-tabs>
        </Card>
      </div>
    </div>
  </div>
</template>
