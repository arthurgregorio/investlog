<script setup lang="ts">
import { computed, onMounted, ref, useId, useTemplateRef } from 'vue'
import AddPositionModal, { type PositionEntry } from '@/components/investments/AddPositionModal.vue'
import UpdatePriceModal from '@/components/investments/UpdatePriceModal.vue'
import WithdrawModal from '@/components/investments/WithdrawModal.vue'
import ReinvestModal from '@/components/investments/ReinvestModal.vue'
import EditHoldingTagsModal from '@/components/investments/EditHoldingTagsModal.vue'
import MoveHoldingsModal from '@/components/investments/MoveHoldingsModal.vue'
import HoldingActionsDropdown from '@/components/investments/HoldingActionsDropdown.vue'
import HoldingLedgerTable from '@/components/investments/HoldingLedgerTable.vue'
import FrozenBadge from '@/components/ui/FrozenBadge.vue'
import GainChip from '@/components/ui/GainChip.vue'
import TickerBadge from '@/components/ui/TickerBadge.vue'
import { useDisplayMoney } from '@/composables/useDisplayMoney'
import { fmt } from '@/composables/useFormat'
import { useHoldingDetail } from '@/composables/useHoldingDetail'
import { useAuthStore } from '@/stores/auth'
import { holdingTypeLabel } from '@/utils/holdingLabels'
import { badgeColor } from '@/utils/walletTypes'
import type { LedgerRow } from '@/utils/holdingLedger'
import type { FundHoldingDetail, HoldingRow } from '@/types'

type HoldingModal =
  'addPosition' | 'editPurchase' | 'updatePrice' | 'editTags' | 'withdraw' | 'reinvest' | 'move'

interface DrawerStat {
  testId: string
  label: string
  value?: string
  signedAmount?: number | null
}

const props = defineProps<{ row: HoldingRow; convertToDisplayCurrency: boolean }>()
const emit = defineEmits<{
  close: []
  deleted: []
  positionAdded: []
  relocated: []
}>()

const auth = useAuthStore()
const { displayAmount, formatDisplayAmount } = useDisplayMoney()
const {
  detail,
  loading,
  isFund,
  ledger,
  load,
  refresh,
  toggleFrozen,
  confirmRemoveHolding,
  confirmRemoveEntry,
  confirmRemoveWithdrawal,
} = useHoldingDetail(() => props.row, {
  onChanged: () => emit('positionAdded'),
  onRemoved: () => emit('deleted'),
})

const titleId = useId()
const panel = useTemplateRef<HTMLElement>('panel')
const activeModal = ref<HoldingModal | null>(null)
const editedPurchase = ref<PositionEntry | null>(null)

const displayName = computed(() => props.row.ticker ?? props.row.name)
const secondLine = computed(() => (props.row.ticker ? props.row.name : 'Fundo de investimento'))

const fundDetail = computed(() =>
  isFund.value && detail.value ? (detail.value as FundHoldingDetail) : null,
)

const segmentName = computed(
  () => detail.value?.segmentName ?? props.row.segmentLabel ?? 'Sem segmento',
)

const currentAmount = computed<number | null>(() =>
  isFund.value ? props.row.currentValue : props.row.currentPrice,
)

const gain = computed(() =>
  props.row.gain == null
    ? null
    : displayAmount(props.row.gain, props.row.walletCurrency, props.convertToDisplayCurrency),
)

const firstMovementDate = computed(() => {
  const purchaseDates = ledger.value
    .filter((entry) => entry.type === 'PURCHASE')
    .map((entry) => entry.date)
  return purchaseDates.length ? fmt.date(purchaseDates.sort()[0]) : '—'
})

const realizedResult = computed(() => {
  if (!detail.value || detail.value.withdrawals.length === 0) return null
  const profit = detail.value.withdrawals.reduce((sum, withdrawal) => sum + withdrawal.profit, 0)
  return displayAmount(profit, props.row.walletCurrency, props.convertToDisplayCurrency)
})

const stats = computed<DrawerStat[]>(() => {
  if (fundDetail.value) {
    return [
      { testId: 'stat-invested', label: 'Investido', value: money(props.row.costBasis) },
      { testId: 'stat-first-movement', label: 'Primeiro aporte', value: firstMovementDate.value },
      {
        testId: 'administration-fee-rate',
        label: 'Taxa de administração (% a.a.)',
        value: feeRate(fundDetail.value.administrationFeeRate),
      },
      {
        testId: 'performance-fee-rate',
        label: 'Taxa de performance (%)',
        value: feeRate(fundDetail.value.performanceFeeRate),
      },
    ]
  }
  const { quantity, costBasis } = props.row
  return [
    {
      testId: 'stat-quantity',
      label: 'Quantidade',
      value: quantity == null ? '—' : fmt.qty(quantity),
    },
    {
      testId: 'stat-average-price',
      label: 'Preço médio',
      value: quantity ? money(costBasis / quantity) : '—',
    },
    { testId: 'stat-invested', label: 'Investido', value: money(costBasis) },
    {
      testId: 'stat-realized-result',
      label: 'Resultado realizado',
      signedAmount: realizedResult.value?.amount ?? null,
    },
    { testId: 'stat-first-movement', label: 'Primeira compra', value: firstMovementDate.value },
  ]
})

const cancelOptions = computed(() => (activeModal.value ? [] : ['escape']))

function money(amount: number): string {
  return formatDisplayAmount(amount, props.row.walletCurrency, props.convertToDisplayCurrency)
}

function feeRate(rate: number | null): string {
  return rate == null ? '—' : fmt.pct(rate)
}

function closeOnBackdrop(event: MouseEvent) {
  if ((event.target as HTMLElement).classList.contains('sidebar-background')) emit('close')
}

function closeModal() {
  activeModal.value = null
  editedPurchase.value = null
}

function editPurchase(entry: LedgerRow) {
  editedPurchase.value = {
    id: entry.id,
    date: entry.date,
    quantity: entry.quantity,
    price: entry.unitPrice,
    amount: entry.amount,
  }
  activeModal.value = 'editPurchase'
}

// A full exit flips the holding to COMPLETED, which drops it out of GET /holdings. Reloading the
// detail here would briefly render a holding the list is about to lose, so the drawer closes
// through the same signal a removal uses.
async function onWithdrawn(completed: boolean) {
  if (completed) {
    emit('deleted')
    return
  }
  await refresh()
}

onMounted(() => {
  panel.value?.querySelector<HTMLButtonElement>('[data-drawer-close]')?.focus()
  return load()
})
</script>

<template>
  <b-sidebar
    :model-value="true"
    right
    overlay
    fullheight
    mobile="fullwidth"
    :can-cancel="cancelOptions"
    class="holding-drawer"
    @close="emit('close')"
    @click="closeOnBackdrop"
  >
    <div
      ref="panel"
      class="drawer-panel is-relative"
      role="dialog"
      aria-modal="true"
      :aria-labelledby="titleId"
      data-testid="holding-drawer"
    >
      <b-loading :is-full-page="false" :model-value="loading" />

      <header class="drawer-section is-flex is-flex-direction-column is-gap-2">
        <div class="is-flex is-align-items-flex-start is-gap-1.5">
          <FrozenBadge v-if="row.frozen" />
          <TickerBadge
            v-else
            :ticker="displayName"
            :color="badgeColor(row.ticker, row.kind)"
            :size="46"
          />
          <div class="drawer-identity">
            <h2 :id="titleId" class="drawer-title">{{ displayName }}</h2>
            <p class="t-name">{{ secondLine }}</p>
            <div class="is-flex is-flex-wrap-wrap is-gap-0.5 mt-2">
              <span
                class="type-tag"
                :class="`tt-${row.kind.toLowerCase()}`"
                data-testid="drawer-type"
                >{{ holdingTypeLabel(row) }}</span
              >
              <span class="type-tag" data-testid="drawer-wallet">{{ row.walletName }}</span>
              <span class="type-tag" data-testid="drawer-segment">{{ segmentName }}</span>
            </div>
          </div>
          <b-button
            type="is-ghost"
            icon-left="close"
            aria-label="Fechar detalhe"
            data-drawer-close
            @click="emit('close')"
          />
        </div>

        <div class="is-flex is-justify-content-space-between is-align-items-flex-end is-gap-1.5">
          <div>
            <p class="drawer-label">Valor atual</p>
            <p class="drawer-value c-num" data-testid="drawer-value">
              {{ row.currentValue == null ? '—' : money(row.currentValue) }}
            </p>
            <GainChip
              :value="gain?.amount ?? null"
              :pct="row.gainPct"
              :cur="gain?.currency"
              data-testid="drawer-gain"
            />
          </div>
          <div v-if="!isFund" class="has-text-right">
            <p class="drawer-label">Preço atual</p>
            <p class="has-text-weight-bold c-num" data-testid="drawer-price">
              {{ row.currentPrice == null ? '—' : money(row.currentPrice) }}
            </p>
          </div>
        </div>

        <p v-if="row.frozen" class="frozen-note" role="note">
          <b-icon icon="snowflake" size="is-small" />
          Posição congelada: novas compras ficam bloqueadas.
        </p>

        <div v-if="detail" class="drawer-actions" role="toolbar" aria-label="Ações da posição">
          <b-button
            type="is-primary"
            icon-left="plus"
            expanded
            :disabled="row.frozen"
            data-testid="holding-add-position"
            @click="activeModal = 'addPosition'"
          >
            {{ isFund ? 'Novo aporte' : 'Nova compra' }}
          </b-button>
          <HoldingActionsDropdown
            :is-fund="isFund"
            :is-frozen="row.frozen"
            :is-admin="auth.isAdmin"
            @update-price="activeModal = 'updatePrice'"
            @edit-tags="activeModal = 'editTags'"
            @withdraw="activeModal = 'withdraw'"
            @reinvest="activeModal = 'reinvest'"
            @move="activeModal = 'move'"
            @toggle-frozen="toggleFrozen"
            @remove="confirmRemoveHolding"
          />
        </div>
      </header>

      <template v-if="detail">
        <dl class="drawer-section drawer-stats" data-testid="drawer-stats">
          <div v-for="stat in stats" :key="stat.label" class="drawer-stat">
            <dt class="drawer-label">{{ stat.label }}</dt>
            <dd class="has-text-weight-bold c-num" :data-testid="stat.testId">
              <GainChip
                v-if="stat.signedAmount !== undefined"
                :value="stat.signedAmount"
                :cur="realizedResult?.currency"
              />
              <template v-else>{{ stat.value }}</template>
            </dd>
          </div>
        </dl>

        <section class="drawer-section">
          <HoldingLedgerTable
            :entries="ledger"
            :is-fund="isFund"
            :is-admin="auth.isAdmin"
            :row="row"
            @delete-purchase="confirmRemoveEntry"
            @delete-withdrawal="confirmRemoveWithdrawal"
            @edit-purchase="editPurchase"
          />
        </section>
      </template>
    </div>
  </b-sidebar>

  <AddPositionModal
    v-if="activeModal === 'addPosition' || activeModal === 'editPurchase'"
    :holding-id="row.id"
    :wallet-id="row.walletId"
    :kind="row.kind"
    :wallet-currency="row.walletCurrency"
    :entry="editedPurchase ?? undefined"
    :holding-label="`${displayName} · ${row.walletName}`"
    @added="refresh"
    @updated="refresh"
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

  <EditHoldingTagsModal
    v-if="activeModal === 'editTags' && detail"
    :holding-id="row.id"
    :wallet-id="row.walletId"
    :kind="row.kind"
    :initial-type-id="detail.typeId"
    :initial-segment-id="detail.segmentId"
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
</template>

<style scoped>
.holding-drawer {
  --bulma-sidebar-width: 600px;
  --bulma-sidebar-overlay-background: hsl(
    var(--bulma-scheme-h) var(--bulma-scheme-s) var(--bulma-scheme-invert-l) / 30%
  );
}

.holding-drawer :deep(.sidebar-content) {
  background: var(--surface);
  border-left: 1px solid var(--border);
  box-shadow: var(--shadow-raised);
  max-width: 100vw;
  overflow-y: auto;
}

.drawer-panel {
  min-height: 100%;
}

.drawer-section {
  padding: 18px 20px;
  border-bottom: 1px solid var(--border);
}

.drawer-section:last-child {
  border-bottom: none;
}

.drawer-identity {
  flex: 1 1 auto;
  min-width: 0;
}

.drawer-title {
  font-size: 20px;
  font-weight: 800;
  line-height: 1.2;
}

.drawer-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-muted);
}

.drawer-value {
  font-size: 28px;
  font-weight: 800;
  letter-spacing: -0.01em;
  line-height: 1.2;
}

.frozen-note {
  --ice: var(--bulma-info-on-scheme);

  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border: 1px dashed var(--ice);
  border-radius: 8px;
  background: color-mix(in srgb, var(--ice) 6%, transparent);
  color: var(--ice);
  font-size: 13px;
  font-weight: 600;
}

.drawer-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.drawer-actions :deep(.dropdown),
.drawer-actions :deep(.dropdown-trigger) {
  width: 100%;
}

.drawer-stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
}

.drawer-stat {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 12px 14px;
  border: 1px solid var(--border-2);
  border-radius: 8px;
  background: var(--surface-2);
}

.drawer-stat dd {
  margin: 0;
}
</style>
