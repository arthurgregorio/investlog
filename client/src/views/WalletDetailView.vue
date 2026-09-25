<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useDialog, useToast } from 'buefy'
import AreaChart from '@/components/charts/AreaChart.vue'
import HoldingDetailPanel from '@/components/investments/HoldingDetailPanel.vue'
import MoveHoldingsModal from '@/components/investments/MoveHoldingsModal.vue'
import Card from '@/components/ui/Card.vue'
import CardBody from '@/components/ui/CardBody.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import GainChip from '@/components/ui/GainChip.vue'
import TickerBadge from '@/components/ui/TickerBadge.vue'
import { useAuthStore } from '@/stores/auth'
import { useHoldingsListStore } from '@/stores/holdingsList'
import { walletsApi } from '@/api/wallets'
import { useWalletsStore } from '@/stores/wallets'
import { useWalletDetailStore } from '@/stores/walletDetail'
import { useWalletMovesStore } from '@/stores/walletMoves'
import { fmt } from '@/composables/useFormat'
import { WALLET_TYPES, badgeColor } from '@/utils/walletTypes'
import type { HoldingRow, WalletMoveRow } from '@/types'

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
const moveModalOpen = ref(false)
const holdingToMove = ref<string | undefined>(undefined)

const currency = computed(() => detail.value?.currency ?? 'BRL')

const chartSeries = computed(() => ({
  data: (detail.value?.series ?? []).map((point) => point.currentValue),
  labels: (detail.value?.series ?? []).map((point) => fmt.date(point.snapshotDate)),
}))

const resultDirection = computed(() => {
  const gain = detail.value?.gain
  if (gain == null) return 'gl-empty'
  if (gain > 0.0001) return 'gl-up'
  if (gain < -0.0001) return 'gl-down'
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

function fmtY(value: number) {
  return fmt.money(value, currency.value, { compact: true })
}

async function loadAll() {
  openedDetails.value = []
  await Promise.all([
    walletDetailStore.load(walletId.value),
    holdingsListStore.loadKind('all', 0, { walletId: walletId.value }),
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
  await holdingsListStore.loadKind('all', page - 1, { walletId: walletId.value })
}

async function onHoldingChanged() {
  openedDetails.value = []
  await loadAll()
}

function openMove(row?: HoldingRow) {
  holdingToMove.value = row?.id
  moveModalOpen.value = true
}

async function onMoved() {
  openedDetails.value = []
  await Promise.all([loadAll(), walletsStore.refresh()])
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
    <b-loading :is-full-page="false" :active="walletDetailStore.loading" />

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
              <b-button size="is-small" icon-left="format-list-bulleted" @click="goToHoldings">
                Ver investimentos
              </b-button>
              <b-button
                size="is-small"
                icon-left="swap-horizontal"
                data-testid="wallet-move"
                @click="openMove()"
              >
                Mover
              </b-button>
              <b-button
                v-if="auth.isAdmin"
                size="is-small"
                type="is-danger"
                outlined
                icon-left="delete"
                @click="confirmDeleteWallet"
              >
                Remover
              </b-button>
            </div>
          </div>
        </CardBody>
      </Card>

      <div class="fixed-grid has-3-cols has-1-cols-mobile">
        <div class="grid">
          <div class="cell">
            <Card class="wd-highlight">
              <CardBody class="wd-highlight-body">
                <span class="wd-rail wd-rail-up" />
                <div class="wd-highlight-text">
                  <div class="kpi-label">Melhor desempenho</div>
                  <template v-if="detail.bestPerformer">
                    <div class="wd-highlight-name">
                      {{ detail.bestPerformer.ticker ?? detail.bestPerformer.name }}
                    </div>
                    <GainChip
                      :value="detail.bestPerformer.gain"
                      :pct="detail.bestPerformer.gainPct"
                      :cur="detail.currency"
                      compact
                    />
                  </template>
                  <div v-else class="wd-highlight-empty">—</div>
                </div>
              </CardBody>
            </Card>
          </div>
          <div class="cell">
            <Card class="wd-highlight">
              <CardBody class="wd-highlight-body">
                <span class="wd-rail wd-rail-down" />
                <div class="wd-highlight-text">
                  <div class="kpi-label">Pior desempenho</div>
                  <template v-if="detail.worstPerformer">
                    <div class="wd-highlight-name">
                      {{ detail.worstPerformer.ticker ?? detail.worstPerformer.name }}
                    </div>
                    <GainChip
                      :value="detail.worstPerformer.gain"
                      :pct="detail.worstPerformer.gainPct"
                      :cur="detail.currency"
                      compact
                    />
                  </template>
                  <div v-else class="wd-highlight-empty">—</div>
                </div>
              </CardBody>
            </Card>
          </div>
          <div class="cell">
            <Card class="wd-highlight">
              <CardBody class="wd-highlight-body">
                <span class="wd-rail wd-rail-largest" />
                <div class="wd-highlight-text">
                  <div class="kpi-label">Maior posição</div>
                  <template v-if="detail.largestHoldingName">
                    <div class="wd-highlight-name">{{ largestHoldingTicker }}</div>
                    <div class="wd-share">
                      <span class="wd-share-pct">{{ concentrationLabel }}</span>
                    </div>
                  </template>
                  <div v-else class="wd-highlight-empty">—</div>
                </div>
              </CardBody>
            </Card>
          </div>
        </div>
      </div>

      <Card class="mb-0">
        <CardBody>
          <div class="card-title-row">
            <div>
              <div class="chart-title">Desempenho</div>
              <div class="wd-chart-sub">Valor atual ao longo do tempo</div>
            </div>
            <div v-if="detail.series.length" class="wd-deltas">
              <GainChip :value="detail.dayChange" :cur="detail.currency" compact />
              <GainChip :value="detail.weekChange" :cur="detail.currency" compact />
              <GainChip :value="detail.monthChange" :cur="detail.currency" compact />
            </div>
          </div>

          <div v-if="detail.series.length" class="chart-wrap">
            <AreaChart
              :data="chartSeries.data"
              :x-labels="chartSeries.labels"
              color="var(--primary)"
              :height="260"
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

      <EmptyState
        v-if="holdingsListStore.loaded && holdingsListStore.rows.length === 0"
        icon="wallet-outline"
        title="Nenhum investimento nesta carteira"
        text="Adicione um investimento para começar a acompanhar esta carteira."
      />

      <Card v-else class="table-card">
        <div class="table-wrap">
          <b-loading :is-full-page="false" :active="holdingsListStore.loading" />
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
                  <tr class="inv-row" :class="{ 'is-open': isOpen(row) }" @click="toggleRow(row)">
                    <td>
                      <div class="name-cell">
                        <TickerBadge
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
                    <td class="c-num">{{ row.quantity == null ? '—' : fmt.qty(row.quantity) }}</td>
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
                      <b-tooltip label="Mover" position="is-left">
                        <b-button
                          type="is-ghost"
                          size="is-small"
                          icon-left="swap-horizontal"
                          aria-label="Mover investimento"
                          data-testid="row-move"
                          @click.stop="openMove(row)"
                        />
                      </b-tooltip>
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
      </Card>

      <Card class="table-card" data-testid="move-history">
        <div class="move-history-title">
          <div class="chart-title">Movimentações</div>
          <div class="wd-chart-sub">Investimentos movidos de e para esta carteira</div>
        </div>
        <div v-if="walletMovesStore.rows.length > 0" class="table-wrap">
          <b-loading :is-full-page="false" :active="walletMovesStore.loading" />
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
        <EmptyState
          v-else-if="walletMovesStore.loaded"
          icon="swap-horizontal"
          title="Nenhuma movimentação"
          text="Investimentos movidos entre carteiras aparecem aqui."
        />
      </Card>

      <MoveHoldingsModal
        v-if="moveModalOpen"
        :origin-wallet-id="detail.id"
        :preselected-holding-id="holdingToMove"
        @moved="onMoved"
        @close="moveModalOpen = false"
      />
    </template>
  </div>
</template>
