<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { BButton, useDialog, useToast } from 'buefy'
import AddPositionModal from '@/components/investments/AddPositionModal.vue'
import UpdatePriceModal from '@/components/investments/UpdatePriceModal.vue'
import WithdrawModal from '@/components/investments/WithdrawModal.vue'
import DateInput from '@/components/ui/DateInput.vue'
import GainChip from '@/components/ui/GainChip.vue'
import { holdingsApi } from '@/api/holdings'
import { resultsApi } from '@/api/results'
import { useCurrencyStore } from '@/stores/currency'
import { useAuthStore } from '@/stores/auth'
import { fmt } from '@/composables/useFormat'
import { buildLedger } from '@/utils/holdingLedger'
import type { FundHoldingDetail, HoldingDetail, HoldingRow, StockHoldingDetail } from '@/types'

const props = defineProps<{ row: HoldingRow }>()
const emit = defineEmits<{
  deleted: []
  positionAdded: []
}>()

const dialog = useDialog()
const toast = useToast()
const currencyStore = useCurrencyStore()
const auth = useAuthStore()

const detail = ref<HoldingDetail | null>(null)
const loading = ref(false)
const showAddPositionModal = ref(false)
const showUpdatePriceModal = ref(false)
const showWithdrawModal = ref(false)

const isFund = computed(() => props.row.kind === 'FUNDS')
const isStock = computed(() => props.row.kind === 'STOCKS')

const currentAmount = computed<number | null>(() => {
  if (!detail.value) return null
  return isFund.value
    ? (detail.value as FundHoldingDetail).currentValue
    : (detail.value as StockHoldingDetail).currentPrice
})

// One merged, chronological ledger of purchases/aportes and withdrawals — see holdingLedger.ts.
// This is the only view of a holding's history; there is no separate purchases-only table.
const ledgerRows = computed(() => (detail.value ? buildLedger(detail.value, isFund.value) : []))

function signedQty(value: number): string {
  return (value >= 0 ? '+' : '−') + fmt.qty(Math.abs(value))
}

onMounted(async () => {
  loading.value = true
  try {
    if (isStock.value) {
      detail.value = await holdingsApi.getStockHolding(props.row.walletId, props.row.id)
    } else if (props.row.kind === 'CRYPTO') {
      detail.value = await holdingsApi.getCryptoHolding(props.row.walletId, props.row.id)
    } else {
      detail.value = await holdingsApi.getFundHolding(props.row.walletId, props.row.id)
    }
  } finally {
    loading.value = false
  }
})

async function reloadDetail() {
  if (isStock.value) {
    detail.value = await holdingsApi.getStockHolding(props.row.walletId, props.row.id)
  } else if (props.row.kind === 'CRYPTO') {
    detail.value = await holdingsApi.getCryptoHolding(props.row.walletId, props.row.id)
  } else {
    detail.value = await holdingsApi.getFundHolding(props.row.walletId, props.row.id)
  }
}

async function onPositionAdded() {
  await reloadDetail()
  emit('positionAdded')
}

async function onPriceUpdated() {
  await reloadDetail()
  emit('positionAdded')
}

// A full exit flips the holding to COMPLETED, which drops it out of GET /holdings. Reloading the
// detail here would briefly render a holding the list is about to lose, so the panel collapses
// through the same signal a removal uses.
async function onWithdrawn(completed: boolean) {
  if (completed) {
    emit('deleted')
    return
  }
  await reloadDetail()
  emit('positionAdded')
}

async function confirmRemove() {
  dialog.confirm({
    title: 'Remover investimento',
    message: 'Esta ação <strong>não pode ser desfeita</strong>.',
    type: 'is-danger',
    hasIcon: true,
    confirmText: 'Remover',
    cancelText: 'Cancelar',
    onConfirm: async () => {
      if (isStock.value) {
        await holdingsApi.deleteStockHolding(props.row.walletId, props.row.id)
      } else if (props.row.kind === 'CRYPTO') {
        await holdingsApi.deleteCryptoHolding(props.row.walletId, props.row.id)
      } else {
        await holdingsApi.deleteFundHolding(props.row.walletId, props.row.id)
      }
      emit('deleted')
    },
  })
}

function confirmDeletePurchase(purchaseId: string) {
  dialog.confirm({
    title: isFund.value ? 'Remover aporte' : 'Remover compra',
    message: 'Esta ação <strong>não pode ser desfeita</strong>.',
    type: 'is-danger',
    hasIcon: true,
    confirmText: 'Remover',
    cancelText: 'Cancelar',
    onConfirm: async () => {
      if (isFund.value) {
        await holdingsApi.deleteFundContribution(props.row.walletId, props.row.id, purchaseId)
        toast.open({ message: 'Aporte removido.', type: 'is-success' })
      } else if (isStock.value) {
        await holdingsApi.deleteStockLot(props.row.walletId, props.row.id, purchaseId)
        toast.open({ message: 'Compra removida.', type: 'is-success' })
      } else {
        await holdingsApi.deleteCryptoLot(props.row.walletId, props.row.id, purchaseId)
        toast.open({ message: 'Compra removida.', type: 'is-success' })
      }
      await reloadDetail()
      emit('positionAdded')
    },
  })
}

function confirmDeleteWithdrawal(resultId: string) {
  dialog.confirm({
    title: isFund.value ? 'Desfazer resgate' : 'Desfazer venda',
    message: 'Esta ação <strong>não pode ser desfeita</strong>.',
    type: 'is-danger',
    hasIcon: true,
    confirmText: 'Desfazer',
    cancelText: 'Cancelar',
    onConfirm: async () => {
      try {
        if (isFund.value) {
          await resultsApi.deleteFundWithdrawal(props.row.walletId, props.row.id, resultId)
          toast.open({ message: 'Resgate desfeito.', type: 'is-success' })
        } else if (isStock.value) {
          await resultsApi.deleteStockWithdrawal(props.row.walletId, props.row.id, resultId)
          toast.open({ message: 'Venda desfeita.', type: 'is-success' })
        } else {
          await resultsApi.deleteCryptoWithdrawal(props.row.walletId, props.row.id, resultId)
          toast.open({ message: 'Venda desfeita.', type: 'is-success' })
        }
        await reloadDetail()
        emit('positionAdded')
      } catch {
        // The api client's response interceptor already shows the server's rejection as a
        // toast (e.g. "Apenas o resgate mais recente pode ser desfeito.") — nothing else to do.
      }
    },
  })
}

const editingDateId = ref<string | null>(null)

function parseDate(iso: string): Date {
  return new Date(iso + 'T00:00:00')
}

function toIsoDate(date: Date): string {
  return date.toISOString().slice(0, 10)
}

function startEditDate(id: string) {
  editingDateId.value = id
}

async function savePurchaseDate(purchaseId: string, date: Date | null) {
  if (!date) return
  const isoDate = toIsoDate(date)
  if (isFund.value) {
    await holdingsApi.updateFundContributionDate(props.row.walletId, props.row.id, purchaseId, {
      contributionDate: isoDate,
    })
  } else if (isStock.value) {
    await holdingsApi.updateStockLotDate(props.row.walletId, props.row.id, purchaseId, {
      lotDate: isoDate,
    })
  } else {
    await holdingsApi.updateCryptoLotDate(props.row.walletId, props.row.id, purchaseId, {
      lotDate: isoDate,
    })
  }
  editingDateId.value = null
  toast.open({ message: 'Data atualizada.', type: 'is-success' })
  await reloadDetail()
  emit('positionAdded')
}
</script>

<template>
  <div class="detail">
    <b-loading :is-full-page="false" :active="loading" />

    <div v-if="detail" class="ledger-head">
      <div class="ledger-head-info">
        <span class="ledger-title">Movimentações</span>
        <span class="ledger-count">{{ ledgerRows.length }}</span>
      </div>
      <div class="ledger-actions">
        <b-button
          size="is-small"
          type="is-success"
          outlined
          icon-left="plus"
          @click="showAddPositionModal = true"
        >
          {{ isFund ? 'Registrar novo aporte' : 'Registrar nova compra' }}
        </b-button>
        <b-button
          size="is-small"
          type="is-info"
          outlined
          icon-left="pencil"
          @click="showUpdatePriceModal = true"
        >
          {{ isFund ? 'Atualizar valor atual' : 'Atualizar preço' }}
        </b-button>
        <b-button
          size="is-small"
          type="is-warning"
          outlined
          icon-left="cash-minus"
          @click="showWithdrawModal = true"
        >
          Resgatar
        </b-button>
        <b-button v-if="auth.isAdmin" outlined type="is-danger" size="is-small" icon-left="delete" @click="confirmRemove">
          Remover
        </b-button>
      </div>
    </div>

    <table v-if="detail" class="sub-table">
      <thead>
        <tr>
          <th>Tipo</th>
          <th>{{ isFund ? 'Data do aporte' : 'Data da compra' }}</th>
          <th v-if="!isFund" class="c-num">Qtd.</th>
          <th v-if="!isFund" class="c-num">Preço unit.</th>
          <th class="c-num">Custos</th>
          <th class="c-num">Valor</th>
          <th class="c-num">Resultado</th>
          <th v-if="!isFund" class="c-num">Saldo</th>
          <th class="c-act"></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="entry in ledgerRows" :key="entry.id">
          <td>
            <span
              class="ledger-tag"
              :class="entry.type === 'WITHDRAWAL' ? 'lt-withdrawal' : 'lt-purchase'"
            >
              {{ entry.type === 'WITHDRAWAL' ? (isFund ? 'Resgate' : 'Venda') : isFund ? 'Aporte' : 'Compra' }}
            </span>
          </td>
          <td>
            <template v-if="entry.type === 'PURCHASE'">
              <DateInput
                v-if="editingDateId === entry.id"
                :model-value="parseDate(entry.date)"
                @update:model-value="(date) => savePurchaseDate(entry.id, date)"
              />
              <button v-else class="date-edit" @click.stop="startEditDate(entry.id)">
                {{ fmt.date(entry.date) }}
              </button>
            </template>
            <template v-else>{{ fmt.date(entry.date) }}</template>
          </td>
          <td v-if="!isFund" class="c-num">
            {{ entry.quantity == null ? '—' : signedQty(entry.quantity) }}
          </td>
          <td v-if="!isFund" class="c-num">
            <template v-if="entry.unitPrice != null">
              {{ fmt.money(currencyStore.convert(entry.unitPrice, row.walletCurrency), currencyStore.displayCurrency) }}
            </template>
            <span v-else class="gl-empty">—</span>
          </td>
          <td class="c-num">
            <template v-if="entry.costs != null">
              {{ fmt.money(currencyStore.convert(entry.costs, row.walletCurrency), currencyStore.displayCurrency) }}
              <div class="ledger-costs-note">
                taxa {{ fmt.money(currencyStore.convert(entry.fees ?? 0, row.walletCurrency), currencyStore.displayCurrency) }}
                + imp. {{ fmt.money(currencyStore.convert(entry.taxes ?? 0, row.walletCurrency), currencyStore.displayCurrency) }}
              </div>
            </template>
            <span v-else class="gl-empty">—</span>
          </td>
          <td class="c-num">
            {{ fmt.money(currencyStore.convert(entry.amount, row.walletCurrency), currencyStore.displayCurrency) }}
          </td>
          <td class="c-num">
            <GainChip
              v-if="entry.profit != null"
              :value="currencyStore.convert(entry.profit, row.walletCurrency)"
              :cur="currencyStore.displayCurrency"
            />
            <span v-else class="gl-empty">—</span>
          </td>
          <td v-if="!isFund" class="c-num">
            {{ entry.balance == null ? '—' : fmt.qty(entry.balance) }}
          </td>
          <td class="c-act">
            <b-button
              v-if="auth.isAdmin"
              outlined
              type="is-danger"
              size="is-small"
              icon-left="delete"
              @click.stop="
                entry.type === 'PURCHASE'
                  ? confirmDeletePurchase(entry.id)
                  : confirmDeleteWithdrawal(entry.id)
              "
            />
          </td>
        </tr>
      </tbody>
    </table>

    <AddPositionModal
      v-if="showAddPositionModal"
      :holding-id="row.id"
      :wallet-id="row.walletId"
      :kind="row.kind"
      :wallet-currency="row.walletCurrency"
      @added="onPositionAdded"
      @close="showAddPositionModal = false"
    />

    <UpdatePriceModal
      v-if="showUpdatePriceModal"
      :holding-id="row.id"
      :wallet-id="row.walletId"
      :kind="row.kind"
      :wallet-currency="row.walletCurrency"
      :initial-value="currentAmount"
      @updated="onPriceUpdated"
      @close="showUpdatePriceModal = false"
    />

    <WithdrawModal
      v-if="showWithdrawModal"
      :holding-id="row.id"
      :wallet-id="row.walletId"
      :kind="row.kind"
      :wallet-currency="row.walletCurrency"
      :remaining-quantity="row.quantity"
      :current-value="row.currentValue"
      @withdrawn="onWithdrawn"
      @close="showWithdrawModal = false"
    />
  </div>
</template>
