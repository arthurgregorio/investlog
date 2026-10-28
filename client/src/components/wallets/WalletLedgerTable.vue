<script setup lang="ts">
import EmptyState from '@/components/ui/EmptyState.vue'
import GainChip from '@/components/ui/GainChip.vue'
import TablePagination from '@/components/ui/TablePagination.vue'
import { cashApi } from '@/api/cash'
import { fmt } from '@/composables/useFormat'
import { safeHtml, useConfirmDialog } from '@/composables/useConfirmDialog'
import { useWalletDetailStore } from '@/stores/walletDetail'
import { useWalletLedgerStore } from '@/stores/walletLedger'
import { useWalletsStore } from '@/stores/wallets'
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
  TRANSFER_IN: { label: 'Transferência recebida', icon: 'bank-transfer-in', tone: 'is-in' },
  TRANSFER_OUT: { label: 'Transferência enviada', icon: 'bank-transfer-out', tone: 'is-out' },
  CASH_WITHDRAWAL: { label: 'Saque para o banco', icon: 'bank-outline', tone: 'is-out' },
}

const CASH_EVENTS: WalletLedgerEventType[] = ['TRANSFER_IN', 'TRANSFER_OUT', 'CASH_WITHDRAWAL']

const props = defineProps<{ walletId: string; currency: string }>()

const walletLedgerStore = useWalletLedgerStore()
const walletDetailStore = useWalletDetailStore()
const walletsStore = useWalletsStore()
const { confirm } = useConfirmDialog()

function isMove(entry: WalletLedgerEntry): boolean {
  return entry.type === 'MOVE_IN' || entry.type === 'MOVE_OUT'
}

function isCashEvent(entry: WalletLedgerEntry): boolean {
  return CASH_EVENTS.includes(entry.type)
}

function hasProfit(entry: WalletLedgerEntry): boolean {
  return entry.type === 'WITHDRAWAL' || entry.type === 'REINVESTMENT'
}

function investment(entry: WalletLedgerEntry): string {
  return entry.ticker ?? entry.holdingName ?? 'Caixa'
}

function fundingOf(entry: WalletLedgerEntry): string {
  const cashUsed = entry.cashUsed ?? 0
  const newMoney = entry.newMoney ?? 0
  if (cashUsed > 0 && newMoney > 0) {
    return `Do caixa ${fmt.money(cashUsed, props.currency)} · novo ${fmt.money(newMoney, props.currency)}`
  }
  if (cashUsed > 0) return `Do caixa ${fmt.money(cashUsed, props.currency)}`
  if (newMoney > 0) return 'Dinheiro novo'
  return ''
}

function transferInDetail(entry: WalletLedgerEntry): string {
  const origin = `← ${entry.counterpartName}`
  const { originalAmount, originalCurrency, rate } = entry
  if (originalAmount == null || rate == null || !originalCurrency) return origin
  if (originalCurrency === props.currency) return origin
  return `${origin} · ${fmt.money(originalAmount, originalCurrency)} a ${fmt.money(rate, props.currency)}`
}

function detail(entry: WalletLedgerEntry): string {
  switch (entry.type) {
    case 'MOVE_IN':
    case 'MOVE_OUT':
      return entry.counterpartName ?? 'Carteira removida'
    case 'REINVESTMENT':
      return entry.counterpartName ? `→ ${entry.counterpartName}` : ''
    case 'WITHDRAWAL':
      return '→ caixa'
    case 'TRANSFER_IN':
      return transferInDetail(entry)
    case 'TRANSFER_OUT':
      return `→ ${entry.counterpartName}`
    case 'CASH_WITHDRAWAL':
      return entry.counterpartName ? `→ ${entry.counterpartName}` : ''
    default:
      return fundingOf(entry)
  }
}

function quantity(entry: WalletLedgerEntry): string {
  if (entry.quantity != null) return fmt.qty(entry.quantity)
  return isMove(entry) ? 'Tudo' : '—'
}

function amount(entry: WalletLedgerEntry): string {
  return entry.amount == null ? '—' : fmt.money(entry.amount, props.currency)
}

function deleteCashEvent(entry: WalletLedgerEntry) {
  const label = EVENT_TYPES[entry.type].label.toLowerCase()
  confirm({
    title: 'Excluir movimentação',
    message: safeHtml`Excluir a ${label} de ${fmt.date(entry.date)} no valor de ${amount(entry)}?`,
    confirmText: 'Excluir',
    onConfirm: async () => {
      if (entry.type === 'CASH_WITHDRAWAL') {
        await cashApi.deleteBankWithdrawal(props.walletId, entry.id)
      } else {
        await cashApi.deleteTransfer(props.walletId, entry.id)
      }
      await Promise.all([
        walletLedgerStore.refresh(),
        walletDetailStore.refresh(),
        walletsStore.refresh(),
      ])
    },
  })
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
              <th class="c-num has-text-right">Saldo em caixa</th>
              <th class="c-act"><span class="is-sr-only">Ações</span></th>
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
                <span class="t-ticker">{{ investment(entry) }}</span>
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
              <td
                class="c-num has-text-right has-text-weight-semibold"
                data-testid="ledger-cash-balance"
              >
                {{ fmt.money(entry.cashBalance, currency) }}
              </td>
              <td class="c-act has-text-right">
                <b-button
                  v-if="isCashEvent(entry)"
                  type="is-danger"
                  outlined
                  size="is-small"
                  icon-left="delete"
                  aria-label="Excluir movimentação"
                  data-testid="ledger-delete-cash-event"
                  @click="deleteCashEvent(entry)"
                />
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
        text="Compras, aportes, resgates, transferências e saques desta carteira aparecem aqui."
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
