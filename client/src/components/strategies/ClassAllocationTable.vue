<script setup lang="ts">
import { fmt } from '@/composables/useFormat'
import { chartColor } from '@/utils/allocation'
import {
  driftLabel,
  driftTagType,
  holdingCountLabel,
  moneyToTarget,
  targetLabel,
} from '@/utils/allocationLabels'
import type { ClassAllocation, StrategyAllocation } from '@/types'

defineProps<{ allocation: StrategyAllocation }>()
const expandedClassId = defineModel<string | null>('expandedClassId', { required: true })

function toggle(classId: string) {
  expandedClassId.value = expandedClassId.value === classId ? null : classId
}

function classCaption(strategyClass: ClassAllocation): string {
  const frozenCount = strategyClass.holdings.filter((holding) => holding.frozen).length
  const holdingsText = holdingCountLabel(strategyClass.holdings.length, 'ativo')
  if (frozenCount === 0) return holdingsText
  return `${holdingsText} · ${frozenCount} ${frozenCount === 1 ? 'congelado' : 'congelados'}`
}
</script>

<template>
  <div class="table-wrap">
    <p
      v-if="allocation.classes.length === 0"
      class="card-body has-text-grey"
      data-testid="allocation-no-classes"
    >
      Esta estratégia ainda não tem classes. Crie-as na aba Classes e metas.
    </p>
    <div v-else class="table-scroll">
      <table class="inv-table">
        <thead>
          <tr>
            <th>Classe</th>
            <th class="c-num has-text-right">Meta</th>
            <th class="c-num has-text-right">Atual</th>
            <th class="c-num has-text-right">Desvio</th>
            <th class="c-num has-text-right">Para a meta</th>
            <th class="c-num has-text-right">Valor atual</th>
            <th class="c-act is-wide"><span class="is-sr-only">Ações</span></th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="(strategyClass, index) in allocation.classes"
            :key="strategyClass.id"
            class="inv-row"
            :class="{ 'is-current': expandedClassId === strategyClass.id }"
            data-testid="allocation-class-row"
          >
            <td>
              <div class="is-flex is-align-items-center is-gap-1.5">
                <span class="wref-dot" :style="{ background: chartColor(index) }" />
                <div class="name-meta">
                  <button
                    type="button"
                    class="t-ticker open-holding"
                    @click="toggle(strategyClass.id)"
                  >
                    {{ strategyClass.name }}
                  </button>
                  <div class="t-name">{{ classCaption(strategyClass) }}</div>
                </div>
              </div>
            </td>
            <td class="c-num has-text-right">{{ targetLabel(strategyClass.targetPercentage) }}</td>
            <td class="c-num has-text-right has-text-weight-semibold">
              {{ fmt.pct(strategyClass.actualPercentage) }}
            </td>
            <td class="c-num has-text-right">
              <span class="tag" :class="driftTagType(strategyClass.driftPercentagePoints)">
                {{ driftLabel(strategyClass.driftPercentagePoints) }}
              </span>
            </td>
            <td class="c-num has-text-right">
              {{ moneyToTarget(strategyClass.driftValue, allocation.displayCurrency) }}
            </td>
            <td class="c-num has-text-right has-text-weight-bold">
              {{ fmt.money(strategyClass.value, allocation.displayCurrency) }}
            </td>
            <td class="c-act is-wide">
              <div class="is-flex is-justify-content-flex-end">
                <b-button
                  type="is-ghost"
                  :icon-left="expandedClassId === strategyClass.id ? 'chevron-up' : 'chevron-down'"
                  :aria-label="`Investimentos de ${strategyClass.name}`"
                  data-testid="expand-allocation-class"
                  @click="toggle(strategyClass.id)"
                />
              </div>
            </td>
          </tr>
          <tr class="inv-row" data-testid="allocation-total-row">
            <td><span class="has-text-weight-bold">Total</span></td>
            <td class="c-num has-text-right">100%</td>
            <td class="c-num has-text-right has-text-weight-semibold">100%</td>
            <td />
            <td />
            <td class="c-num has-text-right has-text-weight-bold">
              {{ fmt.money(allocation.totalValue, allocation.displayCurrency) }}
            </td>
            <td />
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
