<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { BButton, useDialog, useToast } from 'buefy'
import AddPositionModal from '@/components/investments/AddPositionModal.vue'
import UpdatePriceModal from '@/components/investments/UpdatePriceModal.vue'
import WithdrawModal from '@/components/investments/WithdrawModal.vue'
import ReinvestModal from '@/components/investments/ReinvestModal.vue'
import SetSegmentModal from '@/components/investments/SetSegmentModal.vue'
import MoveHoldingsModal from '@/components/investments/MoveHoldingsModal.vue'
import DateInput from '@/components/ui/DateInput.vue'
import FrozenBadge from '@/components/ui/FrozenBadge.vue'
import GainChip from '@/components/ui/GainChip.vue'
import { holdingsApi } from '@/api/holdings'
import { resultsApi } from '@/api/results'
import { useCurrencyStore } from '@/stores/currency'
import { useAuthStore } from '@/stores/auth'
import { fmt } from '@/composables/useFormat'
import { buildLedger, type LedgerMovementType } from '@/utils/holdingLedger'
import type { FundHoldingDetail, HoldingDetail, HoldingRow, StockHoldingDetail } from '@/types'

const props = defineProps<{ row: HoldingRow }>()
const emit = defineEmits<{
  deleted: []
  positionAdded: []
  relocated: []
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
const showReinvestModal = ref(false)
const showMoveModal = ref(false)
const showSetSegmentModal = ref(false)

const isFund = computed(() => props.row.kind === 'FUNDS')
const isStock = computed(() => props.row.kind === 'STOCKS')
const isFrozen = computed(() => props.row.frozen)

const fundDetail = computed(() =>
  isFund.value && detail.value ? (detail.value as FundHoldingDetail) : null,
)

const stockDetail = computed(() =>
  isStock.value && detail.value ? (detail.value as StockHoldingDetail) : null,
)

const currentAmount = computed<number | null>(() => {
  if (!detail.value) return null
  return fundDetail.value
    ? fundDetail.value.currentValue
    : (detail.value as StockHoldingDetail).currentPrice
})

function formatFeeRate(rate: number | null): string {
  return rate == null ? '—' : fmt.pct(rate)
}

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

function openAddPosition() {
  if (isFrozen.value) return
  showAddPositionModal.value = true
}

async function toggleFrozen() {
  const frozen = !isFrozen.value
  if (isStock.value) {
    await holdingsApi.updateStockHolding(props.row.walletId, props.row.id, { frozen })
  } else if (props.row.kind === 'CRYPTO') {
    await holdingsApi.updateCryptoHolding(props.row.walletId, props.row.id, { frozen })
  } else {
    await holdingsApi.updateFundHolding(props.row.walletId, props.row.id, { frozen })
  }
  toast.open({
    message: frozen ? 'Investimento congelado.' : 'Investimento descongelado.',
    type: 'is-success',
  })
  await reloadDetail()
  emit('positionAdded')
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

function movementLabel(type: LedgerMovementType): string {
  if (type === 'REINVESTMENT') return 'Reinvestimento'
  if (type === 'WITHDRAWAL') return isFund.value ? 'Resgate' : 'Venda'
  return isFund.value ? 'Aporte' : 'Compra'
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
    <b-loading :is-full-page="false" :model-value="loading" />

    <div v-if="fundDetail" class="fund-fees" data-testid="fund-fees">
      <div class="fund-fee">
        <span class="fund-fee-label">Taxa de administração (% a.a.)</span>
        <span class="fund-fee-value" data-testid="administration-fee-rate">{{
          formatFeeRate(fundDetail.administrationFeeRate)
        }}</span>
      </div>
      <div class="fund-fee">
        <span class="fund-fee-label">Taxa de performance (%)</span>
        <span class="fund-fee-value" data-testid="performance-fee-rate">{{
          formatFeeRate(fundDetail.performanceFeeRate)
        }}</span>
      </div>
    </div>

    <div v-if="detail" class="ledger-head">
      <div class="ledger-head-info">
        <span class="ledger-title">Movimentações</span>
        <span class="ledger-count">{{ ledgerRows.length }}</span>
        <FrozenBadge v-if="isFrozen" compact />
      </div>
      <div class="ledger-actions">
        <b-dropdown
          aria-role="list"
          position="is-bottom-left"
          append-to-body
          data-testid="holding-actions"
        >
          <template #trigger>
            <b-button size="is-small" icon-right="menu-down">Ações</b-button>
          </template>

          <b-dropdown-item
            aria-role="listitem"
            :disabled="isFrozen"
            data-testid="holding-add-position"
            @click="openAddPosition"
          >
            <b-icon icon="plus" size="is-small" />
            {{ isFund ? 'Registrar novo aporte' : 'Registrar nova compra' }}
          </b-dropdown-item>
          <b-dropdown-item aria-role="listitem" @click="showUpdatePriceModal = true">
            <b-icon icon="pencil" size="is-small" />
            {{ isFund ? 'Atualizar valor atual' : 'Atualizar preço' }}
          </b-dropdown-item>
          <b-dropdown-item
            v-if="isStock"
            aria-role="listitem"
            data-testid="holding-set-segment"
            @click="showSetSegmentModal = true"
          >
            <b-icon icon="tag-outline" size="is-small" /> Definir segmento
          </b-dropdown-item>
          <b-dropdown-item aria-role="listitem" @click="showWithdrawModal = true">
            <b-icon icon="cash-minus" size="is-small" /> Resgatar
          </b-dropdown-item>
          <b-dropdown-item
            aria-role="listitem"
            data-testid="holding-reinvest"
            @click="showReinvestModal = true"
          >
            <b-icon icon="autorenew" size="is-small" /> Reinvestir
          </b-dropdown-item>
          <b-dropdown-item
            aria-role="listitem"
            data-testid="holding-move"
            @click="showMoveModal = true"
          >
            <b-icon icon="swap-horizontal" size="is-small" /> Mover
          </b-dropdown-item>
          <b-dropdown-item
            aria-role="listitem"
            data-testid="holding-freeze"
            @click="toggleFrozen"
          >
            <b-icon :icon="isFrozen ? 'snowflake-off' : 'snowflake'" size="is-small" />
            {{ isFrozen ? 'Descongelar' : 'Congelar' }}
          </b-dropdown-item>
          <template v-if="auth.isAdmin">
            <hr class="dropdown-divider" />
            <b-dropdown-item
              aria-role="listitem"
              class="has-text-danger"
              data-testid="holding-remove"
              @click="confirmRemove"
            >
              <b-icon icon="delete" size="is-small" /> Remover
            </b-dropdown-item>
          </template>
        </b-dropdown>
      </div>
    </div>

    <table v-if="detail" class="sub-table">
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
        <tr v-for="entry in ledgerRows" :key="entry.id">
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
              {{
                fmt.money(
                  currencyStore.convert(entry.unitPrice, row.walletCurrency),
                  currencyStore.displayCurrency,
                )
              }}
            </template>
            <span v-else class="gl-empty">—</span>
          </td>
          <td class="c-num">
            <template v-if="entry.costs != null">
              {{
                fmt.money(
                  currencyStore.convert(entry.costs, row.walletCurrency),
                  currencyStore.displayCurrency,
                )
              }}
              <div class="ledger-costs-note">
                taxa
                {{
                  fmt.money(
                    currencyStore.convert(entry.fees ?? 0, row.walletCurrency),
                    currencyStore.displayCurrency,
                  )
                }}
                + imp.
                {{
                  fmt.money(
                    currencyStore.convert(entry.taxes ?? 0, row.walletCurrency),
                    currencyStore.displayCurrency,
                  )
                }}
              </div>
            </template>
            <span v-else class="gl-empty">—</span>
          </td>
          <td class="c-num">
            {{
              fmt.money(
                currencyStore.convert(entry.amount, row.walletCurrency),
                currencyStore.displayCurrency,
              )
            }}
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
              v-if="auth.isAdmin && entry.type !== 'REINVESTMENT'"
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
      :initial-administration-fee-rate="fundDetail?.administrationFeeRate ?? null"
      :initial-performance-fee-rate="fundDetail?.performanceFeeRate ?? null"
      @updated="onPriceUpdated"
      @close="showUpdatePriceModal = false"
    />

    <SetSegmentModal
      v-if="showSetSegmentModal"
      :holding-id="row.id"
      :wallet-id="row.walletId"
      :initial-segment-id="stockDetail?.stockSegmentId ?? null"
      @updated="onPriceUpdated"
      @close="showSetSegmentModal = false"
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

    <ReinvestModal
      v-if="showReinvestModal"
      :wallet-id="row.walletId"
      :preselected-holding-id="row.id"
      @reinvested="emit('relocated')"
      @close="showReinvestModal = false"
    />

    <MoveHoldingsModal
      v-if="showMoveModal"
      :origin-wallet-id="row.walletId"
      :preselected-holding-id="row.id"
      @moved="emit('relocated')"
      @close="showMoveModal = false"
    />
  </div>
</template>
