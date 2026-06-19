<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import type { IconName } from '@/components/AppIcon.vue'
import Card from '@/components/ui/Card.vue'
import CardBody from '@/components/ui/CardBody.vue'
import AreaChart from '@/components/charts/AreaChart.vue'
import DonutChart from '@/components/charts/DonutChart.vue'
import { useOverviewStore } from '@/stores/overview'
import { useWalletsStore } from '@/stores/wallets'
import { useModals } from '@/composables/useModals'
import { fmt } from '@/composables/useFormat'
import { WALLET_TYPES } from '@/utils/walletTypes'
import type { WalletKind } from '@/types'

const overviewStore = useOverviewStore()
const walletsStore = useWalletsStore()
const router = useRouter()
useModals()

onMounted(() => {
  Promise.all([overviewStore.load(), walletsStore.load()])
})

const summary = computed(() => overviewStore.summary)
const baseCurrency = computed(() => summary.value?.baseCurrency ?? 'BRL')
const totalHoldings = computed(() =>
  summary.value?.kindSummaries.reduce((sum, ks) => sum + ks.holdingCount, 0) ?? 0,
)

const walletCountByKind = computed(() => {
  const counts: Record<string, number> = {}
  for (const wallet of walletsStore.wallets) {
    counts[wallet.kind] = (counts[wallet.kind] ?? 0) + 1
  }
  return counts
})

const ALL_KINDS: WalletKind[] = ['STOCKS', 'CRYPTO', 'FUNDS']

const typeRows = computed(() => {
  const summaryByKind = Object.fromEntries(
    (summary.value?.kindSummaries ?? []).map((kindSummary) => [kindSummary.kind, kindSummary]),
  )
  return ALL_KINDS.map((kind) => {
    const kindSummary = summaryByKind[kind]
    return {
      key: kind,
      label: WALLET_TYPES[kind].label,
      accent: WALLET_TYPES[kind].accent,
      invested: kindSummary?.totalCostBasis ?? 0,
      walletCount: walletCountByKind.value[kind] ?? 0,
      holdings: kindSummary?.holdingCount ?? 0,
    }
  })
})

const grandInvestedBase = computed(() => summary.value?.totalCostBasis ?? 0)

const chartSeries = computed(() => {
  const points = overviewStore.series
  if (!points.length) return { data: [0, 0], labels: ['', ''] }
  const data = points.map((point) => point.totalInvested)
  const labels = points.map((point) => fmt.dateShort(point.month + '-01'))
  if (data.length < 2) {
    data.unshift(0)
    labels.unshift('')
  }
  return { data, labels }
})

const segments = computed(() => {
  const segs = typeRows.value
    .filter((typeRow) => typeRow.invested > 0)
    .map((typeRow) => ({ value: typeRow.invested, color: typeRow.accent, label: typeRow.label }))
  return segs.length ? segs : [{ value: 1, color: 'var(--chart-grid)', label: '—' }]
})

const lastSeries = computed(() => {
  const data = chartSeries.value.data
  return data[data.length - 1] ?? 0
})

function fmtY(value: number) {
  return fmt.sym(baseCurrency.value) + ' ' + (value >= 1000 ? (value / 1000).toFixed(0) + 'k' : value.toFixed(0))
}

function gotoType(key: WalletKind) {
  router.push({ name: 'investments', query: { filter: key } })
}

const iconFor = (key: WalletKind): IconName => WALLET_TYPES[key].icon as IconName
</script>

<template>
  <div class="page">
    <b-loading :is-full-page="false" :active="overviewStore.loading" />

    <div class="page-head page-head-row">
      <div>
        <div class="page-eyebrow">Logbook</div>
        <h1 class="page-title">Visão geral</h1>
      </div>
      <div class="head-actions">
        <b-button type="is-primary" icon-left="wallet" @click="router.push({ name: 'wallets' })">Carteiras</b-button>
      </div>
    </div>

    <template v-if="summary">
      <div class="fixed-grid has-3-cols has-1-cols-mobile">
        <div class="grid">
          <div class="cell">
            <Card class="kpi-card">
              <CardBody>
                <div class="kpi-label">Total investido</div>
                <div class="kpi-value">{{ fmt.money(grandInvestedBase, baseCurrency) }}</div>
                <div class="kpi-foot">
                  <span class="kpi-sub">
                    {{ walletsStore.wallets.length }} carteiras · {{ totalHoldings }} investimentos
                  </span>
                </div>
              </CardBody>
            </Card>
          </div>

          <div class="cell">
            <Card class="kpi-card">
              <CardBody>
                <div class="kpi-label">Valor atual estimado</div>
                <div class="kpi-value">{{ fmt.money(summary.totalCurrentValue, baseCurrency) }}</div>
                <div class="kpi-foot">
                  <span class="kpi-sub">posições com valor atual</span>
                </div>
              </CardBody>
            </Card>
          </div>

          <div class="cell">
            <Card class="kpi-card">
              <CardBody>
                <div class="kpi-label">Resultado</div>
                <div class="kpi-value" :class="summary.totalGain >= 0 ? 'gl-up' : 'gl-down'">
                  {{ fmt.moneySigned(summary.totalGain, baseCurrency) }}
                </div>
                <div class="kpi-foot">
                  <span class="kpi-sub" :class="summary.totalGain >= 0 ? 'gl-up' : 'gl-down'">
                    {{ fmt.pctSigned(summary.totalGainPct ?? 0) }}
                  </span>
                  <span class="kpi-sub">sobre posições avaliadas</span>
                </div>
              </CardBody>
            </Card>
          </div>
        </div>
      </div>

      <div class="fixed-grid has-3-cols has-1-cols-mobile">
        <div class="grid">
          <div class="cell is-col-span-2">
            <Card class="chart-card">
              <CardBody>
                <div class="card-title-row">
                  <div>
                    <div class="chart-title">Evolução dos aportes</div>
                    <div class="chart-sub">Capital investido acumulado · {{ baseCurrency }}</div>
                  </div>
                  <div class="chart-big">{{ fmt.money(lastSeries, baseCurrency, { compact: true }) }}</div>
                </div>
                <div class="chart-wrap">
                  <AreaChart
                    :data="chartSeries.data"
                    :x-labels="chartSeries.labels"
                    color="var(--primary)"
                    :height="252"
                    :fmt-y="fmtY"
                  />
                </div>
              </CardBody>
            </Card>
          </div>

          <div class="cell">
            <Card class="alloc-card">
              <CardBody>
                <div class="card-title-row"><div class="chart-title">Alocação por tipo</div></div>
                <div class="alloc-body">
                  <DonutChart :segments="segments" :size="156" :thickness="22">
                    <div class="donut-center-label">Investido</div>
                    <div class="donut-center-value">{{ fmt.money(grandInvestedBase, baseCurrency, { compact: true }) }}</div>
                  </DonutChart>
                  <ul class="alloc-legend">
                    <li v-for="typeRow in typeRows" :key="typeRow.key">
                      <span class="legend-dot" :style="{ background: typeRow.accent }" />
                      <span class="legend-label">{{ typeRow.label }}</span>
                      <span class="legend-pct">
                        {{ grandInvestedBase ? fmt.pct((typeRow.invested / grandInvestedBase) * 100) : '0%' }}
                      </span>
                      <span class="legend-value">{{ fmt.money(typeRow.invested, baseCurrency, { compact: true }) }}</span>
                    </li>
                  </ul>
                </div>
              </CardBody>
            </Card>
          </div>
        </div>
      </div>

      <div class="section-label">Distribuição por tipo</div>
      <div class="fixed-grid has-3-cols has-1-cols-mobile">
        <div class="grid">
          <div v-for="typeRow in typeRows" :key="typeRow.key" class="cell">
            <Card class="type-card" @click="gotoType(typeRow.key)">
              <CardBody>
                <div class="type-card-head">
                  <span class="type-ic" :style="{ background: typeRow.accent }">
                    <AppIcon :name="iconFor(typeRow.key)" :size="20" />
                  </span>
                  <div class="type-name">{{ typeRow.label }}</div>
                  <span class="type-share">
                    {{ fmt.pct(grandInvestedBase ? (typeRow.invested / grandInvestedBase) * 100 : 0) }}
                  </span>
                </div>
                <div class="type-value">{{ fmt.money(typeRow.invested, baseCurrency) }}</div>
                <div class="type-bar">
                  <span
                    :style="{
                      width: (grandInvestedBase ? (typeRow.invested / grandInvestedBase) * 100 : 0) + '%',
                      background: typeRow.accent,
                    }"
                  />
                </div>
                <div class="type-meta">
                  <span>{{ typeRow.walletCount }} {{ typeRow.walletCount === 1 ? 'carteira' : 'carteiras' }}</span>
                  <span class="dot">·</span>
                  <span>{{ typeRow.holdings }} {{ typeRow.holdings === 1 ? 'ativo' : 'ativos' }}</span>
                </div>
              </CardBody>
            </Card>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>
