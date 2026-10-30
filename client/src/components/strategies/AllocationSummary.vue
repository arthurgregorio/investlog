<script setup lang="ts">
import { computed } from 'vue'
import KpiCard from '@/components/ui/KpiCard.vue'
import { fmt } from '@/composables/useFormat'
import { driftLabel, holdingCountLabel, moneyToTarget } from '@/utils/allocationLabels'
import type { StrategyAllocation } from '@/types'

const props = defineProps<{ allocation: StrategyAllocation }>()
const emit = defineEmits<{ distribute: [] }>()

const currency = computed(() => props.allocation.displayCurrency)
const holdings = computed(() =>
  props.allocation.classes.flatMap((strategyClass) => strategyClass.holdings),
)

const largestDrift = computed(() =>
  props.allocation.classes.reduce<StrategyAllocation['classes'][number] | null>(
    (largest, strategyClass) =>
      !largest ||
      Math.abs(strategyClass.driftPercentagePoints) > Math.abs(largest.driftPercentagePoints)
        ? strategyClass
        : largest,
    null,
  ),
)

const frozen = computed(() => holdings.value.filter((holding) => holding.frozen))
const frozenValue = computed(() =>
  frozen.value.reduce((total, holding) => total + holding.value, 0),
)
</script>

<template>
  <b-message
    v-if="allocation.unassignedHoldingCount > 0"
    type="is-info"
    icon="information-outline"
    has-icon
    class="mb-0"
    data-testid="unassigned-notice"
  >
    <div
      class="is-flex is-align-items-center is-justify-content-space-between is-gap-3 is-flex-wrap-wrap"
    >
      <div>
        <strong
          >{{ holdingCountLabel(allocation.unassignedHoldingCount) }}
          {{ allocation.unassignedHoldingCount === 1 ? 'ainda está' : 'ainda estão' }} em Sem
          estratégia · Não definida · {{ fmt.money(allocation.unassignedValue, currency) }}</strong
        ><br />
        Eles não contam em nenhuma meta até serem distribuídos entre as classes das suas
        estratégias.
      </div>
      <b-button
        type="is-info"
        icon-left="swap-horizontal"
        data-testid="notice-distribute"
        @click="emit('distribute')"
      >
        Distribuir investimentos
      </b-button>
    </div>
  </b-message>

  <div class="fixed-grid has-3-cols has-1-cols-mobile mb-0">
    <div class="grid">
      <div class="cell">
        <KpiCard
          label="Valor atual da estratégia"
          :value="fmt.money(allocation.totalValue, currency, { compact: true })"
          value-test-id="kpi-strategy-value"
        >
          <template #foot>
            <span class="kpi-sub"
              >{{ holdingCountLabel(holdings.length) }} em
              {{ holdingCountLabel(allocation.classes.length, 'classe') }}</span
            >
          </template>
        </KpiCard>
      </div>
      <div class="cell">
        <KpiCard
          label="Maior desvio"
          :value="largestDrift ? driftLabel(largestDrift.driftPercentagePoints) : '—'"
          value-test-id="kpi-largest-drift"
        >
          <template #foot>
            <span v-if="largestDrift" class="kpi-sub"
              >{{ largestDrift.name }} · {{ moneyToTarget(largestDrift.driftValue, currency) }} da
              meta</span
            >
          </template>
        </KpiCard>
      </div>
      <div class="cell">
        <KpiCard
          label="Congelados"
          :value="frozen.length ? holdingCountLabel(frozen.length) : 'Nenhum'"
          value-test-id="kpi-frozen"
        >
          <template #foot>
            <span v-if="frozen.length" class="kpi-sub"
              >{{ fmt.money(frozenValue, currency, { compact: true }) }} · contam no desvio, fora do
              planejador</span
            >
          </template>
        </KpiCard>
      </div>
    </div>
  </div>
</template>
