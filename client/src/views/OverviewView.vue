<script setup lang="ts">
import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import type { IconName } from '@/components/AppIcon.vue'
import Card from '@/components/ui/Card.vue'
import CardBody from '@/components/ui/CardBody.vue'
import GainChip from '@/components/ui/GainChip.vue'
import AreaChart from '@/components/charts/AreaChart.vue'
import DonutChart from '@/components/charts/DonutChart.vue'
import { usePortfolioStore } from '@/stores/portfolio'
import { useModals } from '@/composables/useModals'
import { fmt } from '@/composables/useFormat'
import type { WalletKind } from '@/types'

const store = usePortfolioStore()
const { base, wallets, holdings, totalsByType, grandInvestedBase, currentSummary, cumulativeSeries } =
  storeToRefs(store)
const router = useRouter()
const modals = useModals()

const segments = computed(() => {
  const segs = totalsByType.value
    .filter((t) => t.invested > 0)
    .map((t) => ({ value: t.invested, color: t.accent, label: t.label }))
  return segs.length ? segs : [{ value: 1, color: 'var(--chart-grid)', label: '—' }]
})

const lastSeries = computed(() => cumulativeSeries.value.data[cumulativeSeries.value.data.length - 1])

function fmtY(v: number) {
  return fmt.sym(base.value) + ' ' + (v >= 1000 ? (v / 1000).toFixed(0) + 'k' : v.toFixed(0))
}

function gotoType(key: WalletKind) {
  router.push({ name: 'investments', query: { filter: key } })
}

const iconFor = (key: WalletKind): IconName => store.WALLET_TYPES[key].icon as IconName
</script>

<template>
  <div class="page">
    <div class="page-head page-head-row">
      <div>
        <div class="page-eyebrow">Logbook</div>
        <h1 class="page-title">Visão geral</h1>
      </div>
      <div class="head-actions">
        <b-button icon-left="wallet" @click="router.push({ name: 'wallets' })">Carteiras</b-button>
        <b-button type="is-primary" icon-left="plus" @click="modals.openAddInvestment()">Adicionar</b-button>
      </div>
    </div>

    <div class="kpi-grid">
      <Card class="kpi-card">
        <CardBody>
          <div class="kpi-label">Total investido</div>
          <div class="kpi-value">{{ fmt.money(grandInvestedBase, base) }}</div>
          <div class="kpi-foot">
            <span class="kpi-sub">{{ wallets.length }} carteiras · {{ holdings.length }} investimentos</span>
          </div>
        </CardBody>
      </Card>

      <Card class="kpi-card">
        <CardBody>
          <div class="kpi-label">Valor atual estimado</div>
          <div class="kpi-value">
            {{ currentSummary.withCV ? fmt.money(currentSummary.currentBase, base) : '—' }}
          </div>
          <div class="kpi-foot">
            <span class="kpi-sub">
              {{
                currentSummary.withCV
                  ? `${currentSummary.withCV} de ${currentSummary.total} com valor atual`
                  : 'preencha o valor atual'
              }}
            </span>
          </div>
        </CardBody>
      </Card>

      <Card class="kpi-card">
        <CardBody>
          <div class="kpi-label">Resultado</div>
          <div class="kpi-value">
            <GainChip
              v-if="currentSummary.withCV"
              :value="currentSummary.gain"
              :pct="currentSummary.pct"
              :cur="base"
            />
            <template v-else>—</template>
          </div>
          <div class="kpi-foot"><span class="kpi-sub">sobre posições avaliadas</span></div>
        </CardBody>
      </Card>
    </div>

    <div class="grid-8-4">
      <Card class="chart-card">
        <CardBody>
          <div class="card-title-row">
            <div>
              <div class="chart-title">Evolução dos aportes</div>
              <div class="chart-sub">Capital investido acumulado · {{ base }}</div>
            </div>
            <div class="chart-big">{{ fmt.money(lastSeries, base, { compact: true }) }}</div>
          </div>
          <div class="chart-wrap">
            <AreaChart
              :data="cumulativeSeries.data"
              :x-labels="cumulativeSeries.labels"
              color="var(--primary)"
              :height="252"
              :fmt-y="fmtY"
            />
          </div>
        </CardBody>
      </Card>

      <Card class="alloc-card">
        <CardBody>
          <div class="card-title-row"><div class="chart-title">Alocação por tipo</div></div>
          <div class="alloc-body">
            <DonutChart :segments="segments" :size="156" :thickness="22">
              <div class="donut-center-label">Investido</div>
              <div class="donut-center-value">{{ fmt.money(grandInvestedBase, base, { compact: true }) }}</div>
            </DonutChart>
            <ul class="alloc-legend">
              <li v-for="t in totalsByType" :key="t.key">
                <span class="legend-dot" :style="{ background: t.accent }" />
                <span class="legend-label">{{ t.label }}</span>
                <span class="legend-pct">
                  {{ grandInvestedBase ? fmt.pct((t.invested / grandInvestedBase) * 100) : '0%' }}
                </span>
                <span class="legend-value">{{ fmt.money(t.invested, base, { compact: true }) }}</span>
              </li>
            </ul>
          </div>
        </CardBody>
      </Card>
    </div>

    <div class="section-label">Distribuição por tipo</div>
    <div class="type-grid">
      <Card v-for="t in totalsByType" :key="t.key" class="type-card" @click="gotoType(t.key)">
        <CardBody>
          <div class="type-card-head">
            <span class="type-ic" :style="{ background: t.accent }"><AppIcon :name="iconFor(t.key)" :size="20" /></span>
            <div class="type-name">{{ t.label }}</div>
            <span class="type-share">{{ fmt.pct(grandInvestedBase ? (t.invested / grandInvestedBase) * 100 : 0) }}</span>
          </div>
          <div class="type-value">{{ fmt.money(t.invested, base) }}</div>
          <div class="type-bar">
            <span :style="{ width: (grandInvestedBase ? (t.invested / grandInvestedBase) * 100 : 0) + '%', background: t.accent }" />
          </div>
          <div class="type-meta">
            <span>{{ t.wallets }} {{ t.wallets === 1 ? 'carteira' : 'carteiras' }}</span>
            <span class="dot">·</span>
            <span>{{ t.holdings }} {{ t.holdings === 1 ? 'ativo' : 'ativos' }}</span>
          </div>
        </CardBody>
      </Card>
    </div>
  </div>
</template>
