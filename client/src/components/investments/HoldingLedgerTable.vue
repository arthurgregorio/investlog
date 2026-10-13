<script setup lang="ts">
import GainChip from '@/components/ui/GainChip.vue'
import { fmt } from '@/composables/useFormat'
import { useDisplayMoney } from '@/composables/useDisplayMoney'
import type { LedgerMovementType, LedgerRow } from '@/utils/holdingLedger'
import type { HoldingRow } from '@/types'

const props = defineProps<{
  entries: LedgerRow[]
  isFund: boolean
  isAdmin: boolean
  row: HoldingRow
}>()

const emit = defineEmits<{
  'delete-purchase': [entryId: string]
  'delete-withdrawal': [resultId: string]
  'edit-purchase': [entry: LedgerRow]
}>()

const { formatConverted, toDisplayCurrency } = useDisplayMoney()

function money(amount: number): string {
  return formatConverted(amount, props.row.walletCurrency)
}

function displayAmount(amount: number) {
  return toDisplayCurrency(amount, props.row.walletCurrency)
}

function movementLabel(type: LedgerMovementType): string {
  if (type === 'REINVESTMENT') return 'Reinvestimento'
  if (type === 'WITHDRAWAL') return props.isFund ? 'Resgate' : 'Venda'
  return props.isFund ? 'Aporte' : 'Compra'
}

function tagClass(type: LedgerMovementType): string {
  if (type === 'PURCHASE') return 'lt-purchase'
  return type === 'REINVESTMENT' ? 'lt-reinvestment' : 'lt-withdrawal'
}

const AMOUNT_ONLY_LABELS: Record<LedgerMovementType, string> = {
  PURCHASE: 'Valor aportado',
  WITHDRAWAL: 'Valor resgatado',
  REINVESTMENT: 'Valor reinvestido',
}

function movementDetail(entry: LedgerRow): string {
  if (entry.quantity == null) return AMOUNT_ONLY_LABELS[entry.type]
  const signedQuantity = (entry.quantity >= 0 ? '+' : '−') + fmt.qty(Math.abs(entry.quantity))
  return entry.unitPrice == null ? signedQuantity : `${signedQuantity} × ${money(entry.unitPrice)}`
}

function deleteEntry(entry: LedgerRow) {
  if (entry.type === 'PURCHASE') emit('delete-purchase', entry.id)
  else emit('delete-withdrawal', entry.id)
}
</script>

<template>
  <div class="is-flex is-align-items-center is-gap-1 mb-1">
    <h3 class="ledger-title">Movimentações</h3>
    <span class="ledger-count">{{ entries.length }}</span>
  </div>

  <ul class="ledger-list">
    <li v-for="entry in entries" :key="entry.id" class="ledger-item c-num">
      <div class="is-flex is-flex-direction-column is-align-items-flex-start is-gap-0.5">
        <span class="ledger-tag" :class="tagClass(entry.type)">
          {{ movementLabel(entry.type) }}
        </span>
        <span class="has-text-grey" data-testid="ledger-date">{{ fmt.date(entry.date) }}</span>
      </div>
      <div class="is-flex is-flex-direction-column is-gap-0.5">
        <span data-testid="ledger-detail">{{ movementDetail(entry) }}</span>
        <span v-if="entry.costs != null" class="ledger-note" data-testid="ledger-costs">
          taxas + impostos {{ money(entry.costs) }}
        </span>
      </div>
      <div class="is-flex is-flex-direction-column is-align-items-flex-end is-gap-0.5">
        <span class="has-text-weight-bold" data-testid="ledger-amount">{{
          money(entry.amount)
        }}</span>
        <GainChip
          v-if="entry.profit != null"
          :value="displayAmount(entry.profit).amount"
          :cur="displayAmount(entry.profit).currency"
          data-testid="ledger-result"
        />
      </div>
      <div class="is-flex is-justify-content-flex-end is-gap-0.5">
        <b-button
          v-if="entry.type === 'PURCHASE'"
          type="is-ghost"
          size="is-small"
          icon-left="pencil"
          :aria-label="`Editar ${movementLabel(entry.type).toLowerCase()} de ${fmt.date(entry.date)}`"
          data-testid="ledger-edit"
          @click="emit('edit-purchase', entry)"
        />
        <b-button
          v-if="isAdmin && entry.type !== 'REINVESTMENT'"
          outlined
          type="is-danger"
          size="is-small"
          icon-left="delete"
          :aria-label="`Remover ${movementLabel(entry.type).toLowerCase()} de ${fmt.date(entry.date)}`"
          data-testid="ledger-delete"
          @click="deleteEntry(entry)"
        />
      </div>
    </li>
  </ul>
</template>

<style scoped>
.ledger-title {
  font-size: 14px;
  font-weight: 700;
}

.ledger-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--tag-bg);
  color: var(--text-2);
  font-size: 11px;
  font-weight: 700;
}

.ledger-list {
  display: grid;
  grid-template-columns: minmax(84px, 1fr) minmax(0, 1.3fr) auto auto;
  column-gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.ledger-item {
  display: grid;
  grid-column: 1 / -1;
  grid-template-columns: subgrid;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid var(--border-2);
  font-size: 13px;
}

.ledger-item:last-child {
  border-bottom: none;
}

.ledger-tag {
  display: inline-flex;
  font-size: 9.5px;
  font-weight: 700;
  letter-spacing: 0.03em;
  text-transform: uppercase;
  padding: 2px 6px;
  border-radius: 5px;
}

.lt-purchase {
  background: var(--up-soft);
  color: var(--up);
}

.lt-withdrawal {
  background: var(--withdrawal-soft);
  color: var(--withdrawal-text);
}

.lt-reinvestment {
  background: var(--primary-soft);
  color: var(--primary-text);
}

.ledger-note {
  font-size: 11px;
  color: var(--text-muted);
}
</style>
