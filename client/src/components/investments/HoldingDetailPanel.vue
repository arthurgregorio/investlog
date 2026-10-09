<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AddPositionModal from '@/components/investments/AddPositionModal.vue'
import UpdatePriceModal from '@/components/investments/UpdatePriceModal.vue'
import WithdrawModal from '@/components/investments/WithdrawModal.vue'
import ReinvestModal from '@/components/investments/ReinvestModal.vue'
import SetSegmentModal from '@/components/investments/SetSegmentModal.vue'
import MoveHoldingsModal from '@/components/investments/MoveHoldingsModal.vue'
import FundFeeSummary from '@/components/investments/FundFeeSummary.vue'
import HoldingActionsDropdown from '@/components/investments/HoldingActionsDropdown.vue'
import HoldingLedgerTable from '@/components/investments/HoldingLedgerTable.vue'
import { useHoldingDetail } from '@/composables/useHoldingDetail'
import { useAuthStore } from '@/stores/auth'
import type { FundHoldingDetail, HoldingRow, StockHoldingDetail } from '@/types'

type HoldingModal = 'addPosition' | 'updatePrice' | 'setSegment' | 'withdraw' | 'reinvest' | 'move'

const props = defineProps<{ row: HoldingRow }>()
const emit = defineEmits<{
  deleted: []
  positionAdded: []
  relocated: []
}>()

const auth = useAuthStore()
const {
  detail,
  loading,
  editingEntryId,
  isFund,
  ledger,
  load,
  refresh,
  toggleFrozen,
  confirmRemoveHolding,
  confirmRemoveEntry,
  confirmRemoveWithdrawal,
  updateEntryDate,
} = useHoldingDetail(() => props.row, {
  onChanged: () => emit('positionAdded'),
  onRemoved: () => emit('deleted'),
})

const activeModal = ref<HoldingModal | null>(null)

const fundDetail = computed(() =>
  isFund.value && detail.value ? (detail.value as FundHoldingDetail) : null,
)

const stockDetail = computed(() =>
  props.row.kind === 'STOCKS' && detail.value ? (detail.value as StockHoldingDetail) : null,
)

const currentAmount = computed<number | null>(() => {
  if (!detail.value) return null
  return fundDetail.value
    ? fundDetail.value.currentValue
    : (detail.value as StockHoldingDetail).currentPrice
})

function closeModal() {
  activeModal.value = null
}

// A full exit flips the holding to COMPLETED, which drops it out of GET /holdings. Reloading the
// detail here would briefly render a holding the list is about to lose, so the panel collapses
// through the same signal a removal uses.
async function onWithdrawn(completed: boolean) {
  if (completed) {
    emit('deleted')
    return
  }
  await refresh()
}

onMounted(load)
</script>

<template>
  <div class="is-relative pt-1">
    <b-loading :is-full-page="false" :model-value="loading" />

    <FundFeeSummary
      v-if="fundDetail"
      :administration-fee-rate="fundDetail.administrationFeeRate"
      :performance-fee-rate="fundDetail.performanceFeeRate"
    />

    <HoldingLedgerTable
      v-if="detail"
      v-model:editing-entry-id="editingEntryId"
      :entries="ledger"
      :is-fund="isFund"
      :is-admin="auth.isAdmin"
      :row="row"
      @delete-purchase="confirmRemoveEntry"
      @delete-withdrawal="confirmRemoveWithdrawal"
      @change-date="updateEntryDate"
    >
      <template #actions>
        <HoldingActionsDropdown
          :is-fund="isFund"
          :is-stock="row.kind === 'STOCKS'"
          :is-frozen="row.frozen"
          :is-admin="auth.isAdmin"
          @add-position="activeModal = 'addPosition'"
          @update-price="activeModal = 'updatePrice'"
          @set-segment="activeModal = 'setSegment'"
          @withdraw="activeModal = 'withdraw'"
          @reinvest="activeModal = 'reinvest'"
          @move="activeModal = 'move'"
          @toggle-frozen="toggleFrozen"
          @remove="confirmRemoveHolding"
        />
      </template>
    </HoldingLedgerTable>

    <AddPositionModal
      v-if="activeModal === 'addPosition'"
      :holding-id="row.id"
      :wallet-id="row.walletId"
      :kind="row.kind"
      :wallet-currency="row.walletCurrency"
      @added="refresh"
      @close="closeModal"
    />

    <UpdatePriceModal
      v-if="activeModal === 'updatePrice'"
      :holding-id="row.id"
      :wallet-id="row.walletId"
      :kind="row.kind"
      :wallet-currency="row.walletCurrency"
      :initial-value="currentAmount"
      :initial-administration-fee-rate="fundDetail?.administrationFeeRate ?? null"
      :initial-performance-fee-rate="fundDetail?.performanceFeeRate ?? null"
      @updated="refresh"
      @close="closeModal"
    />

    <SetSegmentModal
      v-if="activeModal === 'setSegment'"
      :holding-id="row.id"
      :wallet-id="row.walletId"
      :initial-segment-id="stockDetail?.stockSegmentId ?? null"
      @updated="refresh"
      @close="closeModal"
    />

    <WithdrawModal
      v-if="activeModal === 'withdraw'"
      :holding-id="row.id"
      :wallet-id="row.walletId"
      :kind="row.kind"
      :wallet-currency="row.walletCurrency"
      :remaining-quantity="row.quantity"
      :current-value="row.currentValue"
      @withdrawn="onWithdrawn"
      @close="closeModal"
    />

    <ReinvestModal
      v-if="activeModal === 'reinvest'"
      :wallet-id="row.walletId"
      :preselected-holding-id="row.id"
      @reinvested="emit('relocated')"
      @close="closeModal"
    />

    <MoveHoldingsModal
      v-if="activeModal === 'move'"
      :origin-wallet-id="row.walletId"
      :preselected-holding-id="row.id"
      @moved="emit('relocated')"
      @close="closeModal"
    />
  </div>
</template>
