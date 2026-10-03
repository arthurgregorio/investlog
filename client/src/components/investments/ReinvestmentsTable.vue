<script setup lang="ts">
import { fmt } from '@/composables/useFormat'
import type { ReinvestmentRow, ReinvestmentSide } from '@/types'

defineProps<{ rows: ReinvestmentRow[] }>()

function sideLabel(side: ReinvestmentSide): string {
  return side.ticker ?? side.name
}
</script>

<template>
  <div class="table-scroll">
    <table class="inv-table">
      <thead>
        <tr>
          <th>Data</th>
          <th>Origem</th>
          <th>Destino</th>
          <th class="c-num">Reinvestido</th>
          <th class="c-num">Resultado</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="reinvestment in rows" :key="reinvestment.id" data-testid="reinvestment-row">
          <td>{{ fmt.date(reinvestment.reinvestmentDate) }}</td>
          <td>
            <div class="has-text-weight-bold">{{ sideLabel(reinvestment.source) }}</div>
            <div class="is-size-7 has-text-grey">{{ reinvestment.source.walletName }}</div>
          </td>
          <td>
            <div class="has-text-weight-bold">{{ sideLabel(reinvestment.destination) }}</div>
            <div class="is-size-7 has-text-grey">{{ reinvestment.destination.walletName }}</div>
          </td>
          <td class="c-num">{{ fmt.money(reinvestment.amount, reinvestment.currency) }}</td>
          <td
            class="c-num"
            :class="reinvestment.profit >= 0 ? 'has-text-success' : 'has-text-danger'"
          >
            {{ fmt.moneySigned(reinvestment.profit, reinvestment.currency) }}
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
