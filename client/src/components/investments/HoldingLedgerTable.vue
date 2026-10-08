<script setup lang="ts">
import DateInput from '@/components/ui/DateInput.vue'
import FrozenBadge from '@/components/ui/FrozenBadge.vue'
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
  'change-date': [entryId: string, date: Date]
}>()

const editingEntryId = defineModel<string | null>('editingEntryId', { default: null })

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

function signedQuantity(value: number): string {
  return (value >= 0 ? '+' : '−') + fmt.qty(Math.abs(value))
}

function parseDate(iso: string): Date {
  return new Date(iso + 'T00:00:00')
}

function changeDate(entryId: string, date: Date | null) {
  if (date) emit('change-date', entryId, date)
}

function deleteEntry(entry: LedgerRow) {
  if (entry.type === 'PURCHASE') emit('delete-purchase', entry.id)
  else emit('delete-withdrawal', entry.id)
}
</script>

<template>
  <div class="ledger-head">
    <div class="is-flex is-align-items-center is-gap-1">
      <span class="ledger-title">Movimentações</span>
      <span class="ledger-count">{{ entries.length }}</span>
      <FrozenBadge v-if="row.frozen" compact />
    </div>
    <div class="is-flex is-align-items-center is-flex-wrap-wrap is-gap-1">
      <slot name="actions" />
    </div>
  </div>

  <table class="sub-table">
    <thead>
      <tr>
        <th>Tipo</th>
        <th>{{ isFund ? 'Data do aporte' : 'Data da compra' }}</th>
        <th v-if="!isFund" class="c-num has-text-right">Qtd.</th>
        <th v-if="!isFund" class="c-num has-text-right">Preço unit.</th>
        <th class="c-num has-text-right">Custos</th>
        <th class="c-num has-text-right">Valor</th>
        <th class="c-num has-text-right">Resultado</th>
        <th v-if="!isFund" class="c-num has-text-right">Saldo</th>
        <th class="c-act"></th>
      </tr>
    </thead>
    <tbody>
      <tr v-for="entry in entries" :key="entry.id">
        <td>
          <span
            class="ledger-tag"
            :class="entry.type === 'PURCHASE' ? 'lt-purchase' : 'lt-withdrawal'"
          >
            {{ movementLabel(entry.type) }}
          </span>
        </td>
        <td>
          <template v-if="entry.type === 'PURCHASE'">
            <DateInput
              v-if="editingEntryId === entry.id"
              :model-value="parseDate(entry.date)"
              @update:model-value="(date) => changeDate(entry.id, date)"
            />
            <button v-else class="date-edit" @click.stop="editingEntryId = entry.id">
              {{ fmt.date(entry.date) }}
            </button>
          </template>
          <template v-else>{{ fmt.date(entry.date) }}</template>
        </td>
        <td v-if="!isFund" class="c-num has-text-right">
          {{ entry.quantity == null ? '—' : signedQuantity(entry.quantity) }}
        </td>
        <td v-if="!isFund" class="c-num has-text-right">
          <template v-if="entry.unitPrice != null">{{ money(entry.unitPrice) }}</template>
          <span v-else class="gl-empty">—</span>
        </td>
        <td class="c-num has-text-right">
          <template v-if="entry.costs != null">
            {{ money(entry.costs) }}
            <div class="ledger-costs-note">
              taxa {{ money(entry.fees ?? 0) }} + imp. {{ money(entry.taxes ?? 0) }}
            </div>
          </template>
          <span v-else class="gl-empty">—</span>
        </td>
        <td class="c-num has-text-right">{{ money(entry.amount) }}</td>
        <td class="c-num has-text-right">
          <GainChip
            v-if="entry.profit != null"
            :value="displayAmount(entry.profit).amount"
            :cur="displayAmount(entry.profit).currency"
          />
          <span v-else class="gl-empty">—</span>
        </td>
        <td v-if="!isFund" class="c-num has-text-right">
          {{ entry.balance == null ? '—' : fmt.qty(entry.balance) }}
        </td>
        <td class="c-act">
          <b-button
            v-if="isAdmin && entry.type !== 'REINVESTMENT'"
            outlined
            type="is-danger"
            size="is-small"
            icon-left="delete"
            @click.stop="deleteEntry(entry)"
          />
        </td>
      </tr>
    </tbody>
  </table>
</template>

<style scoped>
.ledger-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin: 4px 2px 10px;
}

.ledger-title {
  font-size: 13px;
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

.sub-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 8px;
  overflow: hidden;
  margin-bottom: 0;
}

.sub-table > thead > tr > th {
  text-align: left;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.03em;
  color: var(--text-muted);
  padding: 9px 12px;
  border-bottom: 1px solid var(--border-2);
}

.sub-table > tbody > tr > td {
  padding: 9px 12px;
  border-bottom: 1px solid var(--border-2);
  color: var(--text-2);
  font-variant-numeric: tabular-nums;
  vertical-align: middle;
}

.sub-table > tbody > tr:last-child > td {
  border-bottom: none;
}

.date-edit {
  background: none;
  border: none;
  padding: 0;
  font: inherit;
  color: inherit;
  cursor: pointer;
  border-bottom: 1px dashed var(--border);
}

.date-edit:hover {
  border-bottom-color: var(--primary);
  color: var(--primary-d);
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

.ledger-costs-note {
  font-size: 9.5px;
  color: var(--text-muted);
  margin-top: 1px;
}
</style>
