<script setup lang="ts">
import GainChip from '@/components/ui/GainChip.vue'
import { fmt } from '@/composables/useFormat'
import type { ResultRow } from '@/types'

defineProps<{ rows: ResultRow[] }>()

function assetLabel(withdrawal: ResultRow): string {
  return withdrawal.ticker ?? withdrawal.holdingName
}

function assetDetail(withdrawal: ResultRow): string {
  if (withdrawal.ticker == null || withdrawal.holdingName === withdrawal.ticker) {
    return withdrawal.walletName
  }
  return `${withdrawal.holdingName} · ${withdrawal.walletName}`
}

function profitPercentage(withdrawal: ResultRow): number | null {
  return withdrawal.costBasis ? (withdrawal.profit / withdrawal.costBasis) * 100 : null
}
</script>

<template>
  <div class="table-scroll">
    <table class="inv-table withdrawals-table">
      <thead>
        <tr>
          <th>Data</th>
          <th>Ativo</th>
          <th class="c-num has-text-right">Quantidade</th>
          <th class="c-num has-text-right">Preço na venda</th>
          <th class="c-num has-text-right">Valor bruto</th>
          <th class="c-num has-text-right">Custos</th>
          <th class="c-num has-text-right">Valor líquido</th>
          <th class="c-num has-text-right">Custo</th>
          <th class="c-num has-text-right">Resultado</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="withdrawal in rows" :key="withdrawal.id" data-testid="withdrawal-row">
          <td>{{ fmt.date(withdrawal.resultDate) }}</td>
          <td>
            <div class="has-text-weight-bold">{{ assetLabel(withdrawal) }}</div>
            <div class="is-size-7 has-text-grey">{{ assetDetail(withdrawal) }}</div>
          </td>
          <td class="c-num has-text-right" data-testid="withdrawal-quantity">
            <span v-if="withdrawal.quantity == null" class="gl-empty">—</span>
            <template v-else>{{ fmt.qty(withdrawal.quantity) }}</template>
          </td>
          <td class="c-num has-text-right" data-testid="withdrawal-unit-price">
            <span v-if="withdrawal.quantity == null" class="gl-empty">—</span>
            <template v-else>
              {{
                fmt.money(withdrawal.grossAmount / withdrawal.quantity, withdrawal.walletCurrency)
              }}
            </template>
          </td>
          <td class="c-num has-text-right" data-testid="withdrawal-gross">
            {{ fmt.money(withdrawal.grossAmount, withdrawal.walletCurrency) }}
          </td>
          <td class="c-num has-text-right" data-testid="withdrawal-costs">
            {{ fmt.money(withdrawal.fees + withdrawal.taxes, withdrawal.walletCurrency) }}
          </td>
          <td class="c-num has-text-right has-text-weight-bold" data-testid="withdrawal-net">
            {{ fmt.money(withdrawal.netAmount, withdrawal.walletCurrency) }}
          </td>
          <td class="c-num has-text-right" data-testid="withdrawal-cost-basis">
            {{ fmt.money(withdrawal.costBasis, withdrawal.walletCurrency) }}
          </td>
          <td class="c-num has-text-right" data-testid="withdrawal-result">
            <GainChip
              :value="withdrawal.profit"
              :pct="profitPercentage(withdrawal)"
              :cur="withdrawal.walletCurrency"
              stacked
            />
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.withdrawals-table > tbody > tr > td.c-num {
  white-space: nowrap;
}
</style>
