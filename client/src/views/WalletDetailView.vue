<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AreaChart from '@/components/charts/AreaChart.vue'
import HoldingDetailPanel from '@/components/investments/HoldingDetailPanel.vue'
import Card from '@/components/ui/Card.vue'
import CardBody from '@/components/ui/CardBody.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import GainChip from '@/components/ui/GainChip.vue'
import TickerBadge from '@/components/ui/TickerBadge.vue'
import { useHoldingsListStore } from '@/stores/holdingsList'
import { useWalletDetailStore } from '@/stores/walletDetail'
import { fmt } from '@/composables/useFormat'
import { WALLET_TYPES, badgeColor } from '@/utils/walletTypes'
import type { HoldingRow } from '@/types'

const CONCENTRATION_WARNING_THRESHOLD = 50

const route = useRoute()
const router = useRouter()
const walletDetailStore = useWalletDetailStore()
const holdingsListStore = useHoldingsListStore()

const walletId = computed(() => route.params.id as string)
const detail = computed(() => walletDetailStore.detail)
const openedDetails = ref<string[]>([])

const currency = computed(() => detail.value?.currency ?? 'BRL')

const chartSeries = computed(() => ({
  data: (detail.value?.series ?? []).map((point) => point.currentValue),
  labels: (detail.value?.series ?? []).map((point) => fmt.date(point.snapshotDate)),
}))

const isConcentrated = computed(
  () =>
    detail.value?.largestHoldingShare != null &&
    detail.value.largestHoldingShare > CONCENTRATION_WARNING_THRESHOLD,
)

const concentrationLabel = computed(() =>
  detail.value?.largestHoldingShare == null ? '' : fmt.pct(detail.value.largestHoldingShare),
)

function fmtY(value: number) {
  return fmt.money(value, currency.value, { compact: true })
}

async function loadAll() {
  openedDetails.value = []
  await Promise.all([
    walletDetailStore.load(walletId.value),
    holdingsListStore.loadKind('all', 0, { walletId: walletId.value }),
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

function goBack() {
  router.push({ name: 'wallets' })
}
</script>

<template>
  <div class="page">
    <b-loading :is-full-page="false" :active="walletDetailStore.loading" />

    <template v-if="detail">
      <div class="page-head">
        <div class="page-head-main">
          <b-button type="is-ghost" icon-left="arrow-left" size="is-small" @click="goBack">
            Carteiras
          </b-button>
          <h1 class="page-title">{{ detail.name }}</h1>
          <span class="type-tag" :class="`tt-${detail.kind.toLowerCase()}`">
            {{ WALLET_TYPES[detail.kind].label }}
          </span>
          <span class="wallet-currency">{{ detail.currency }}</span>
        </div>
      </div>

      <div class="kpi-row">
        <Card>
          <CardBody>
            <div class="kpi-label">Valor atual</div>
            <div class="kpi-value">{{ fmt.money(detail.currentValue, detail.currency) }}</div>
          </CardBody>
        </Card>
        <Card>
          <CardBody>
            <div class="kpi-label">Total investido</div>
            <div class="kpi-value">{{ fmt.money(detail.totalInvested, detail.currency) }}</div>
          </CardBody>
        </Card>
        <Card>
          <CardBody>
            <div class="kpi-label">Resultado</div>
            <GainChip :value="detail.gain" :pct="detail.gainPct" :cur="detail.currency" />
          </CardBody>
        </Card>
        <Card>
          <CardBody>
            <div class="kpi-label">Investimentos</div>
            <div class="kpi-value">{{ detail.activity.investmentCount }}</div>
          </CardBody>
        </Card>
      </div>

      <Card class="perf-card">
        <CardBody>
          <div class="card-head">
            <h2 class="card-title">Desempenho</h2>
            <div v-if="detail.series.length" class="delta-chips">
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
              :height="252"
              :fmt-y="fmtY"
            />
          </div>
          <EmptyState
            v-else
            icon="chart-line"
            title="Ainda sem histórico"
            text="O histórico diário começa a ser registrado a partir da primeira execução do job de snapshot."
          />

          <div class="perf-grid">
            <div v-if="detail.bestPerformer" class="perf-item">
              <div class="kpi-label">Melhor desempenho</div>
              <div class="perf-name">
                {{ detail.bestPerformer.ticker ?? detail.bestPerformer.name }}
              </div>
              <GainChip
                :value="detail.bestPerformer.gain"
                :pct="detail.bestPerformer.gainPct"
                :cur="detail.currency"
                compact
              />
            </div>
            <div v-if="detail.worstPerformer" class="perf-item">
              <div class="kpi-label">Pior desempenho</div>
              <div class="perf-name">
                {{ detail.worstPerformer.ticker ?? detail.worstPerformer.name }}
              </div>
              <GainChip
                :value="detail.worstPerformer.gain"
                :pct="detail.worstPerformer.gainPct"
                :cur="detail.currency"
                compact
              />
            </div>
          </div>

          <b-message v-if="isConcentrated" type="is-warning" has-icon :closable="false">
            <strong>{{ detail.largestHoldingName }}</strong> representa
            {{ concentrationLabel }} desta carteira.
          </b-message>
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
                      <template v-else>{{ fmt.money(row.currentPrice, row.walletCurrency) }}</template>
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
    </template>
  </div>
</template>
