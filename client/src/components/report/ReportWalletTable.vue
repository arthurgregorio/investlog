<script setup lang="ts">
import GainChip from '@/components/ui/GainChip.vue'
import { fmt } from '@/composables/useFormat'
import { useCurrencyStore } from '@/stores/currency'
import type { WalletGroup } from '@/utils/reportGrouping'

defineProps<{
  walletGroup: WalletGroup
}>()

const currencyStore = useCurrencyStore()
</script>

<template>
  <div class="report-wallet-block">
    <div class="report-wallet-name">{{ walletGroup.walletName }}</div>
    <table class="report-table">
      <thead>
        <tr>
          <th>Investimento</th>
          <th class="c-num has-text-right">Qtd.</th>
          <th class="c-num has-text-right">Preço atual</th>
          <th class="c-num has-text-right">Investido</th>
          <th class="c-num has-text-right">Valor atual</th>
          <th class="c-num has-text-right">Resultado</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in walletGroup.rows" :key="row.holding.id">
          <td>{{ row.holding.ticker ?? row.holding.name }}</td>
          <td class="c-num has-text-right">
            {{ row.holding.quantity == null ? '—' : fmt.qty(row.holding.quantity) }}
          </td>
          <td class="c-num has-text-right">
            {{
              row.currentPrice == null
                ? '—'
                : fmt.money(row.currentPrice, currencyStore.displayCurrency)
            }}
          </td>
          <td class="c-num has-text-right">
            {{ fmt.money(row.costBasis, currencyStore.displayCurrency) }}
          </td>
          <td class="c-num has-text-right">
            {{ fmt.money(row.currentValue, currencyStore.displayCurrency) }}
          </td>
          <td class="c-num has-text-right">
            <GainChip :value="row.gain" :pct="row.gainPct" :cur="currencyStore.displayCurrency" />
          </td>
        </tr>
        <tr class="report-subtotal-row">
          <td colspan="3"></td>
          <td class="c-num has-text-right">
            {{ fmt.money(walletGroup.totals.costBasis, currencyStore.displayCurrency) }}
          </td>
          <td class="c-num has-text-right">
            {{ fmt.money(walletGroup.totals.currentValue, currencyStore.displayCurrency) }}
          </td>
          <td class="c-num has-text-right">
            <GainChip
              :value="walletGroup.totals.gain"
              :pct="walletGroup.totals.gainPct"
              :cur="currencyStore.displayCurrency"
            />
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.report-wallet-block {
  margin-bottom: 10px;
  break-inside: avoid;
}

.report-wallet-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-muted);
  margin-bottom: 4px;
  margin-top: 15px;
}

.report-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12.5px;
}

.report-table th,
.report-table td {
  padding: 6px 8px;
  border-bottom: 1px solid var(--border-2);
  text-align: left;
}

.report-table thead th {
  font-weight: 600;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--text-muted);
}

.report-subtotal-row td {
  font-weight: 700;
  border-bottom: none;
  border-top: 1px solid var(--border);
}
</style>
