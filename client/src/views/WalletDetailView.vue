<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useDialog, useToast } from 'buefy'
import AllocationDonut from '@/components/charts/AllocationDonut.vue'
import AreaChart from '@/components/charts/AreaChart.vue'
import HoldingDetailPanel from '@/components/investments/HoldingDetailPanel.vue'
import MoveHoldingsModal from '@/components/investments/MoveHoldingsModal.vue'
import ReinvestModal from '@/components/investments/ReinvestModal.vue'
import Card from '@/components/ui/Card.vue'
import CardBody from '@/components/ui/CardBody.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import GainChip from '@/components/ui/GainChip.vue'
import FrozenBadge from '@/components/ui/FrozenBadge.vue'
import TickerBadge from '@/components/ui/TickerBadge.vue'
import { useAuthStore } from '@/stores/auth'
import { useHoldingsListStore } from '@/stores/holdingsList'
import { holdingsApi } from '@/api/holdings'
import { walletsApi } from '@/api/wallets'
import { useWalletsStore } from '@/stores/wallets'
import { useWalletDetailStore } from '@/stores/walletDetail'
import { useWalletMovesStore } from '@/stores/walletMoves'
import { fmt } from '@/composables/useFormat'
import { badgeColor, WALLET_TYPES } from '@/utils/walletTypes'
import type { HoldingRow, WalletMoveRow } from '@/types'

const HOLDINGS_PAGE_SIZE = 10
const GAIN_EPSILON = 0.0001

const route = useRoute()
const router = useRouter()
const dialog = useDialog()
const toast = useToast()
const auth = useAuthStore()
const walletDetailStore = useWalletDetailStore()
const walletsStore = useWalletsStore()
const holdingsListStore = useHoldingsListStore()
const walletMovesStore = useWalletMovesStore()

const walletId = computed(() => route.params.id as string)
const detail = computed(() => walletDetailStore.detail)
const openedDetails = ref<string[]>([])
const activeTab = ref(0)
const moveModalOpen = ref(false)
const reinvestModalOpen = ref(false)
const allocationRows = ref<HoldingRow[]>([])

const currency = computed(() => detail.value?.currency ?? 'BRL')

const chartSeries = computed(() => ({
  data: (detail.value?.series ?? []).map((point) => point.currentValue),
  labels: (detail.value?.series ?? []).map((point) => fmt.date(point.snapshotDate)),
}))

const deltas = computed(() => [
  { label: '1 dia', value: detail.value?.dayChange ?? null },
  { label: '7 dias', value: detail.value?.weekChange ?? null },
  { label: '30 dias', value: detail.value?.monthChange ?? null },
])

const resultDirection = computed(() => {
  const gain = detail.value?.gain
  if (gain == null) return 'gl-empty'
  if (gain > GAIN_EPSILON) return 'gl-up'
  if (gain < -GAIN_EPSILON) return 'gl-down'
  return 'gl-flat'
})

const concentrationLabel = computed(() =>
  detail.value?.largestHoldingShare == null ? '' : fmt.pct(detail.value.largestHoldingShare),
)

const largestHoldingTicker = computed(() => {
  const currentDetail = detail.value
  if (!currentDetail?.largestHoldingName) return null
  const match = holdingsListStore.rows.find((row) => row.name === currentDetail.largestHoldingName)
  return match?.ticker ?? currentDetail.largestHoldingName
})

const activitySummary = computed(() => {
  const activity = detail.value?.activity
  if (!activity) return ''
  const parts = [
    `${activity.investmentCount} ${activity.investmentCount === 1 ? 'investimento' : 'investimentos'}`,
    `${activity.transactionCount} ${activity.transactionCount === 1 ? 'lançamento' : 'lançamentos'}`,
  ]
  if (activity.walletAgeInDays != null) parts.push(`${activity.walletAgeInDays} dias`)
  return parts.join(' · ')
})

function gainTextClass(gain: number) {
  if (gain > GAIN_EPSILON) return 'has-text-success-on-scheme'
  if (gain < -GAIN_EPSILON) return 'has-text-danger-on-scheme'
  return ''
}

function fmtY(value: number) {
  return fmt.money(value, currency.value, { compact: true })
}

async function loadAllocationRows() {
  const requestedWalletId = walletId.value
  const rows = await holdingsApi.findAllForReport({ walletId: requestedWalletId })
  if (requestedWalletId === walletId.value) allocationRows.value = rows
}

async function loadAll() {
  openedDetails.value = []
  await Promise.all([
    loadAllocationRows(),
    walletDetailStore.load(walletId.value),
    holdingsListStore.loadKind('all', 0, { walletId: walletId.value, size: HOLDINGS_PAGE_SIZE }),
    walletMovesStore.load(walletId.value, 0),
  ])
}

onMounted(loadAll)
watch(walletId, loadAll)

function isOpen(row: HoldingRow) {
  return openedDetails.value.includes(row.id)
}

function toggleRow(row: HoldingRow) {
  openedDetails.value = isOpen(row) ? [] : [row.id]
}

function displayName(row: HoldingRow): string {
  return row.ticker ?? row.name
}

async function onPageChange(page: number) {
  await holdingsListStore.loadKind('all', page - 1, {
    walletId: walletId.value,
    size: HOLDINGS_PAGE_SIZE,
  })
}

async function onHoldingChanged() {
  openedDetails.value = []
  await loadAll()
}

function openMove() {
  moveModalOpen.value = true
}

async function onPositionsChanged() {
  openedDetails.value = []
  await Promise.all([loadAll(), walletsStore.refresh()])
}

function openReinvest() {
  reinvestModalOpen.value = true
}

async function onMovesPageChange(page: number) {
  await walletMovesStore.load(walletId.value, page - 1)
}

function moveCounterpart(move: WalletMoveRow): string {
  const walletName = move.direction === 'OUT' ? move.destinationWalletName : move.originWalletName
  return walletName ?? 'Carteira removida'
}

function goToWallets() {
  router.push({ name: 'wallets' })
}

function goToHoldings() {
  const currentDetail = detail.value
  if (!currentDetail) return
  router.push({
    name: 'investments',
    query: { filter: currentDetail.kind, walletId: currentDetail.id },
  })
}

function renameWallet() {
  const currentDetail = detail.value
  if (!currentDetail) return
  dialog.prompt({
    title: 'Renomear carteira',
    message: 'Novo nome da carteira',
    inputAttrs: { value: currentDetail.name, maxlength: 80 },
    confirmText: 'Salvar',
    cancelText: 'Cancelar',
    trapFocus: true,
    onConfirm: async (newName: string) => {
      const trimmedName = newName.trim()
      if (!trimmedName || trimmedName === currentDetail.name) return
      await walletsApi.update(currentDetail.id, { name: trimmedName })
      toast.open({ message: 'Carteira renomeada.', type: 'is-success' })
      await Promise.all([walletDetailStore.load(currentDetail.id), walletsStore.refresh()])
    },
  })
}

function confirmDeleteWallet() {
  const currentDetail = detail.value
  if (!currentDetail) return
  dialog.confirm({
    title: 'Remover carteira',
    message: `Remover <strong>${currentDetail.name}</strong> apagará todos os seus investimentos. Esta ação <strong>não pode ser desfeita</strong>.`,
    type: 'is-danger',
    hasIcon: true,
    confirmText: 'Remover',
    cancelText: 'Cancelar',
    onConfirm: async () => {
      await walletsApi.remove(currentDetail.id)
      toast.open({ message: 'Carteira removida.', type: 'is-success' })
      await walletsStore.refresh()
      goToWallets()
    },
  })
}
</script>

<template>
  <div class="page">
    <b-loading :is-full-page="false" :model-value="walletDetailStore.loading" />

    <template v-if="detail">
      <div class="wd-breadcrumb">
        <b-button type="is-ghost" icon-left="arrow-left" size="is-small" @click="goToWallets">
          Carteiras
        </b-button>
      </div>

      <Card class="mb-0">
        <CardBody>
          <div class="wd-header-row">
            <div class="wd-identity">
              <span class="wd-kind-mark" :style="{ background: WALLET_TYPES[detail.kind].accent }">
                <b-icon :icon="WALLET_TYPES[detail.kind].icon" size="is-small" />
              </span>
              <div>
                <div class="wd-name-line">
                  <h1 class="page-title">{{ detail.name }}</h1>
                  <b-button
                    type="is-ghost"
                    size="is-small"
                    icon-left="pencil"
                    aria-label="Renomear carteira"
                    @click="renameWallet"
                  />
                </div>
                <div class="wd-meta">
                  <span class="type-tag" :class="`tt-${detail.kind.toLowerCase()}`">
                    {{ WALLET_TYPES[detail.kind].label }}
                  </span>
                  <span class="wd-currency">{{ detail.currency }}</span>
                  <span class="wd-activity">{{ activitySummary }}</span>
                </div>
              </div>
            </div>

            <div class="wd-figures">
              <div class="wd-figure">
                <div class="kpi-label">Total investido</div>
                <div class="wd-figure-value">
                  {{ fmt.money(detail.totalInvested, detail.currency) }}
                </div>
              </div>
              <div class="wd-figure">
                <div class="kpi-label">Valor atual</div>
                <div class="wd-figure-value">
                  {{ fmt.money(detail.currentValue, detail.currency) }}
                </div>
              </div>
              <div class="wd-figure">
                <div class="kpi-label">Resultado</div>
                <div class="wd-figure-value" :class="resultDirection">
                  {{ fmt.moneySigned(detail.gain, detail.currency) }}
                  <span v-if="detail.gainPct != null" class="wd-figure-pct">{{
                    fmt.pctSigned(detail.gainPct)
                  }}</span>
                </div>
              </div>
            </div>

            <div class="wd-actions">
              <b-dropdown aria-role="list" position="is-bottom-left" data-testid="wallet-actions">
                <template #trigger>
                  <b-button size="is-small" icon-right="menu-down">Ações</b-button>
                </template>

                <b-dropdown-item aria-role="listitem" @click="goToHoldings">
                  <b-icon icon="format-list-bulleted" size="is-small" /> Ver investimentos
                </b-dropdown-item>
                <b-dropdown-item aria-role="listitem" data-testid="wallet-move" @click="openMove()">
                  <b-icon icon="swap-horizontal" size="is-small" /> Mover
                </b-dropdown-item>
                <b-dropdown-item
                  aria-role="listitem"
                  data-testid="wallet-reinvest"
                  @click="openReinvest()"
                >
                  <b-icon icon="autorenew" size="is-small" /> Reinvestir
                </b-dropdown-item>
                <template v-if="auth.isAdmin">
                  <hr class="dropdown-divider" />
                  <b-dropdown-item
                    aria-role="listitem"
                    class="has-text-danger"
                    data-testid="wallet-remove"
                    @click="confirmDeleteWallet"
                  >
                    <b-icon icon="delete" size="is-small" /> Remover
                  </b-dropdown-item>
                </template>
              </b-dropdown>
            </div>
          </div>
        </CardBody>

        <div class="wd-highlight-strip">
          <div
            class="wd-highlight-cell is-flex is-align-items-center is-gap-3"
            data-testid="highlight-best"
          >
            <span class="wd-rail wd-rail-up" />
            <div class="is-flex-grow-1">
              <div class="is-size-7 has-text-weight-semibold has-text-grey">Melhor desempenho</div>
              <div
                v-if="detail.bestPerformer"
                class="is-flex is-align-items-center is-justify-content-space-between mt-1"
              >
                <span class="has-text-weight-bold">
                  {{ detail.bestPerformer.ticker ?? detail.bestPerformer.name }}
                </span>
                <span
                  class="has-text-weight-bold is-size-6"
                  :class="gainTextClass(detail.bestPerformer.gainPct)"
                >
                  {{ fmt.pctSigned(detail.bestPerformer.gainPct) }}
                </span>
              </div>
              <div v-else class="has-text-grey mt-1">—</div>
            </div>
          </div>

          <div
            class="wd-highlight-cell is-flex is-align-items-center is-gap-3"
            data-testid="highlight-worst"
          >
            <span class="wd-rail wd-rail-down" />
            <div class="is-flex-grow-1">
              <div class="is-size-7 has-text-weight-semibold has-text-grey">Pior desempenho</div>
              <div
                v-if="detail.worstPerformer"
                class="is-flex is-align-items-center is-justify-content-space-between mt-1"
              >
                <span class="has-text-weight-bold">
                  {{ detail.worstPerformer.ticker ?? detail.worstPerformer.name }}
                </span>
                <span
                  class="has-text-weight-bold is-size-6"
                  :class="gainTextClass(detail.worstPerformer.gainPct)"
                >
                  {{ fmt.pctSigned(detail.worstPerformer.gainPct) }}
                </span>
              </div>
              <div v-else class="has-text-grey mt-1">—</div>
            </div>
          </div>

          <div
            class="wd-highlight-cell is-flex is-align-items-center is-gap-3"
            data-testid="highlight-largest"
          >
            <span class="wd-rail wd-rail-largest" />
            <div class="is-flex-grow-1">
              <div class="is-size-7 has-text-weight-semibold has-text-grey">Maior posição</div>
              <div
                v-if="detail.largestHoldingName"
                class="is-flex is-align-items-center is-justify-content-space-between mt-1"
              >
                <span class="has-text-weight-bold">{{ largestHoldingTicker }}</span>
                <span class="has-text-weight-bold is-size-6">{{ concentrationLabel }}</span>
              </div>
              <div v-else class="has-text-grey mt-1">—</div>
            </div>
          </div>
        </div>
      </Card>

      <Card class="mb-0">
        <CardBody>
          <div
            class="is-flex is-flex-wrap-wrap is-align-items-flex-start is-justify-content-space-between is-gap-3 mb-4"
          >
            <div>
              <div class="chart-title">Desempenho</div>
              <div class="wd-chart-sub">Valor atual ao longo do tempo</div>
            </div>
            <div
              v-if="detail.series.length"
              class="is-flex is-flex-wrap-wrap is-align-items-center is-gap-5"
              data-testid="wallet-deltas"
            >
              <div
                v-for="delta in deltas"
                :key="delta.label"
                class="is-flex is-align-items-center is-gap-2"
              >
                <span class="is-size-7 has-text-grey">{{ delta.label }}</span>
                <GainChip :value="delta.value" :cur="detail.currency" compact />
              </div>
            </div>
          </div>

          <div v-if="detail.series.length" class="chart-wrap">
            <AreaChart
              :data="chartSeries.data"
              :x-labels="chartSeries.labels"
              color="var(--primary)"
              :height="200"
              :fmt-y="fmtY"
            />
          </div>
          <EmptyState
            v-else
            icon="chart-line"
            title="Ainda sem histórico"
            text="O histórico diário começa a ser registrado a partir da primeira execução do job de snapshot."
          />
        </CardBody>
      </Card>

      <Card class="mb-0" data-testid="allocation-card">
        <CardBody>
          <AllocationDonut :rows="allocationRows" :currency="detail.currency" />
        </CardBody>
      </Card>

      <Card class="table-card mb-0">
        <b-tabs v-model="activeTab" class="wd-tabs" :animated="false">
          <b-tab-item>
            <template #header>
              <span>Investimentos</span>
              <span class="tag is-rounded ml-2">{{ holdingsListStore.totalElements }}</span>
            </template>

            <EmptyState
              v-if="holdingsListStore.loaded && holdingsListStore.rows.length === 0"
              icon="wallet-outline"
              title="Nenhum investimento nesta carteira"
              text="Adicione um investimento para começar a acompanhar esta carteira."
            />

            <div v-else class="table-wrap">
              <b-loading :is-full-page="false" :model-value="holdingsListStore.loading" />
              <div class="table-scroll">
                <table class="inv-table">
                  <thead>
                    <tr>
                      <th>Investimento</th>
                      <th class="c-num">Qtd.</th>
                      <th class="c-num">Preço atual</th>
                      <th class="c-num">Investido</th>
                      <th class="c-num">Valor atual</th>
                      <th class="c-num">Resultado</th>
                      <th class="c-act"></th>
                    </tr>
                  </thead>
                  <tbody>
                    <template v-for="row in holdingsListStore.rows" :key="row.id">
                      <tr
                        class="inv-row"
                        :class="{ 'is-open': isOpen(row), 'is-frozen': row.frozen }"
                        @click="toggleRow(row)"
                      >
                        <td>
                          <div class="name-cell">
                            <FrozenBadge v-if="row.frozen" />
                            <TickerBadge
                              v-else
                              :ticker="displayName(row)"
                              :color="badgeColor(row.ticker, row.kind)"
                            />
                            <div class="name-meta">
                              <div class="name-line">
                                <span class="t-ticker">{{ displayName(row) }}</span>
                              </div>
                              <div v-if="row.kind !== 'FUNDS' && row.name" class="t-name">
                                {{ row.name }}
                              </div>
                            </div>
                          </div>
                        </td>
                        <td class="c-num">
                          {{ row.quantity == null ? '—' : fmt.qty(row.quantity) }}
                        </td>
                        <td class="c-num">
                          <span v-if="row.currentPrice == null" class="gl-empty">—</span>
                          <template v-else>{{
                            fmt.money(row.currentPrice, row.walletCurrency)
                          }}</template>
                        </td>
                        <td class="c-num">
                          <div class="cell-strong">
                            {{ fmt.money(row.costBasis, row.walletCurrency) }}
                          </div>
                        </td>
                        <td class="c-num">
                          <span v-if="row.currentValue == null" class="gl-empty">—</span>
                          <template v-else>{{
                            fmt.money(row.currentValue, row.walletCurrency)
                          }}</template>
                        </td>
                        <td class="c-num">
                          <GainChip
                            :value="row.gain"
                            :pct="row.gainPct"
                            :cur="row.walletCurrency"
                            stacked
                          />
                        </td>
                        <td class="c-act">
                          <span class="chev">
                            <b-icon :icon="isOpen(row) ? 'chevron-up' : 'chevron-down'" />
                          </span>
                        </td>
                      </tr>
                      <tr v-if="isOpen(row)" class="detail-row">
                        <td colspan="7">
                          <HoldingDetailPanel
                            :row="row"
                            @deleted="onHoldingChanged"
                            @position-added="onHoldingChanged"
                            @relocated="onPositionsChanged"
                          />
                        </td>
                      </tr>
                    </template>
                  </tbody>
                </table>
              </div>
              <div v-if="holdingsListStore.totalPages > 1" class="table-foot">
                <b-pagination
                  :model-value="holdingsListStore.page + 1"
                  :total="holdingsListStore.totalElements"
                  :per-page="holdingsListStore.pageSize"
                  order="is-right"
                  simple
                  @change="onPageChange"
                />
              </div>
            </div>
          </b-tab-item>

          <b-tab-item>
            <template #header>
              <span>Movimentações</span>
              <span class="tag is-rounded ml-2">{{ walletMovesStore.totalElements }}</span>
            </template>

            <div data-testid="move-history">
              <div v-if="walletMovesStore.rows.length > 0" class="table-wrap">
                <b-loading :is-full-page="false" :model-value="walletMovesStore.loading" />
                <div class="table-scroll">
                  <table class="inv-table">
                    <thead>
                      <tr>
                        <th>Data</th>
                        <th>Investimento</th>
                        <th>Direção</th>
                        <th>Carteira</th>
                        <th class="c-num">Qtd.</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="move in walletMovesStore.rows" :key="move.id" data-testid="move-row">
                        <td>{{ fmt.date(move.movedAt) }}</td>
                        <td>
                          <span class="t-ticker">{{ move.ticker ?? move.holdingName }}</span>
                        </td>
                        <td>
                          <span
                            class="move-direction"
                            :class="move.direction === 'IN' ? 'is-in' : 'is-out'"
                          >
                            <b-icon
                              :icon="move.direction === 'IN' ? 'arrow-bottom-left' : 'arrow-top-right'"
                              size="is-small"
                            />
                            {{ move.direction === 'IN' ? 'Entrada' : 'Saída' }}
                          </span>
                        </td>
                        <td>{{ moveCounterpart(move) }}</td>
                        <td class="c-num">
                          {{ move.quantity == null ? 'Tudo' : fmt.qty(move.quantity) }}
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
                <div v-if="walletMovesStore.totalPages > 1" class="table-foot">
                  <b-pagination
                    :model-value="walletMovesStore.page + 1"
                    :total="walletMovesStore.totalElements"
                    :per-page="walletMovesStore.pageSize"
                    order="is-right"
                    simple
                    @change="onMovesPageChange"
                  />
                </div>
              </div>
              <div
                v-else-if="walletMovesStore.loaded"
                class="move-history-empty"
                data-testid="move-history-empty"
              >
                <EmptyState
                  icon="swap-horizontal"
                  title="Nenhuma movimentação"
                  text="Investimentos movidos entre carteiras aparecem aqui."
                />
              </div>
            </div>
          </b-tab-item>
        </b-tabs>
      </Card>

      <MoveHoldingsModal
        v-if="moveModalOpen"
        :origin-wallet-id="detail.id"
        @moved="onPositionsChanged"
        @close="moveModalOpen = false"
      />

      <ReinvestModal
        v-if="reinvestModalOpen"
        :wallet-id="detail.id"
        @reinvested="onPositionsChanged"
        @close="reinvestModalOpen = false"
      />
    </template>
  </div>
</template>
