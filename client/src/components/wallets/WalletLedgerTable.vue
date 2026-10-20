<script setup lang="ts">
import EmptyState from '@/components/ui/EmptyState.vue'
import GainChip from '@/components/ui/GainChip.vue'
import TablePagination from '@/components/ui/TablePagination.vue'
import { fmt } from '@/composables/useFormat'
import { useWalletLedgerStore } from '@/stores/walletLedger'
import type { WalletLedgerEntry, WalletLedgerEventType } from '@/types'

interface EventTypeMeta {
  label: string
  icon: string
  tone: 'is-in' | 'is-out'
}

const EVENT_TYPES: Record<WalletLedgerEventType, EventTypeMeta> = {
  PURCHASE: { label: 'Compra', icon: 'cart-outline', tone: 'is-in' },
  CONTRIBUTION: { label: 'Aporte', icon: 'cash-plus', tone: 'is-in' },
  WITHDRAWAL: { label: 'Resgate', icon: 'cash-minus', tone: 'is-out' },
  REINVESTMENT: { label: 'Reinvestimento', icon: 'swap-horizontal', tone: 'is-out' },
  MOVE_IN: { label: 'Entrada', icon: 'arrow-bottom-left', tone: 'is-in' },
  MOVE_OUT: { label: 'Saída', icon: 'arrow-top-right', tone: 'is-out' },
}

const props = defineProps<{ walletId: string; currency: string }>()

const walletLedgerStore = useWalletLedgerStore()

function isMove(entry: WalletLedgerEntry): boolean {
  return entry.type === 'MOVE_IN' || entry.type === 'MOVE_OUT'
}

function hasProfit(entry: WalletLedgerEntry): boolean {
  return entry.type === 'WITHDRAWAL' || entry.type === 'REINVESTMENT'
}

function detail(entry: WalletLedgerEntry): string {
  if (isMove(entry)) return entry.counterpartName ?? 'Carteira removida'
  if (entry.type === 'REINVESTMENT' && entry.counterpartName) return `→ ${entry.counterpartName}`
  return ''
}

function quantity(entry: WalletLedgerEntry): string {
  if (entry.quantity != null) return fmt.qty(entry.quantity)
  return isMove(entry) ? 'Tudo' : '—'
}

function amount(entry: WalletLedgerEntry): string {
  return entry.amount == null ? '—' : fmt.money(entry.amount, props.currency)
}

async function onPageChange(page: number) {
  await walletLedgerStore.load(props.walletId, page)
}
</script>

<template>
  <div data-testid="wallet-ledger">
    <div v-if="walletLedgerStore.rows.length > 0" class="table-wrap">
      <b-loading :is-full-page="false" :model-value="walletLedgerStore.loading" />
      <div class="table-scroll">
        <table class="inv-table">
          <thead>
            <tr>
              <th>Data</th>
              <th>Investimento</th>
              <th>Tipo</th>
              <th>Detalhe</th>
              <th class="c-num has-text-right">Qtd.</th>
              <th class="c-num has-text-right">Valor</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="entry in walletLedgerStore.rows"
              :key="`${entry.type}-${entry.id}`"
              data-testid="ledger-row"
            >
              <td>{{ fmt.date(entry.date) }}</td>
              <td>
                <span class="t-ticker">{{ entry.ticker ?? entry.holdingName }}</span>
              </td>
              <td>
                <span class="wallet-ledger-event" :class="EVENT_TYPES[entry.type].tone">
                  <b-icon :icon="EVENT_TYPES[entry.type].icon" size="is-small" />
                  {{ EVENT_TYPES[entry.type].label }}
                </span>
              </td>
              <td data-testid="ledger-detail">{{ detail(entry) }}</td>
              <td class="c-num has-text-right" data-testid="ledger-quantity">
                {{ quantity(entry) }}
              </td>
              <td class="c-num has-text-right" data-testid="ledger-amount">
                <div>{{ amount(entry) }}</div>
                <GainChip v-if="hasProfit(entry)" :value="entry.profit" :cur="currency" />
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <TablePagination
        :page="walletLedgerStore.page"
        :page-size="walletLedgerStore.pageSize"
        :total-elements="walletLedgerStore.totalElements"
        @page-change="onPageChange"
      />
    </div>
    <div
      v-else-if="walletLedgerStore.loaded"
      class="wallet-ledger-empty"
      data-testid="wallet-ledger-empty"
    >
      <EmptyState
        icon="swap-horizontal"
        title="Nenhuma movimentação"
        text="Compras, aportes, resgates e movimentações desta carteira aparecem aqui."
      />
    </div>
  </div>
</template>

<style scoped>
.wallet-ledger-event {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 600;
}

.wallet-ledger-event.is-in {
  color: var(--up);
}

.wallet-ledger-event.is-out {
  color: var(--text-2);
}

.wallet-ledger-empty {
  padding: 16px 20px 20px;
}
</style>
