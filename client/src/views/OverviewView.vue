<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/ui/PageHeader.vue'
import KpiCard from '@/components/ui/KpiCard.vue'
import EvolutionCard from '@/components/overview/EvolutionCard.vue'
import AllocationCard from '@/components/overview/AllocationCard.vue'
import TypeSummaryCard from '@/components/overview/TypeSummaryCard.vue'
import { useOverviewStore } from '@/stores/overview'
import { useWalletsStore } from '@/stores/wallets'
import { useModals } from '@/composables/useModals'
import { fmt } from '@/composables/useFormat'
import { walletKindRows } from '@/utils/walletKindRows'
import type { WalletKind } from '@/types'

const overviewStore = useOverviewStore()
const walletsStore = useWalletsStore()
const router = useRouter()
useModals()

onMounted(() => {
  Promise.all([overviewStore.refresh(), walletsStore.load()])
})

const summary = computed(() => overviewStore.summary)
const baseCurrency = computed(() => summary.value?.displayCurrency ?? 'BRL')
const kindRows = computed(() =>
  walletKindRows(summary.value?.kindSummaries ?? [], walletsStore.wallets),
)
const totalHoldings = computed(() => kindRows.value.reduce((sum, row) => sum + row.holdings, 0))
const resultClass = computed(() => ((summary.value?.totalGain ?? 0) >= 0 ? 'gl-up' : 'gl-down'))

function gotoType(kind: WalletKind) {
  router.push({ name: 'investments', query: { filter: kind } })
}
</script>

<template>
  <div class="page">
    <b-loading :is-full-page="false" :model-value="overviewStore.loading" />

    <PageHeader title="Visão geral" description="Uma visão consolidada dos seus investimentos">
      <b-button type="is-primary" icon-left="wallet" @click="router.push({ name: 'wallets' })"
        >Carteiras</b-button
      >
    </PageHeader>

    <template v-if="summary">
      <div class="fixed-grid has-3-cols has-1-cols-mobile">
        <div class="grid">
          <div class="cell">
            <KpiCard
              data-testid="kpi"
              label="Total investido"
              :value="fmt.money(summary.totalCostBasis, baseCurrency, { compact: true })"
            >
              <template #foot>
                <span class="kpi-sub">
                  {{ walletsStore.wallets.length }} carteiras · {{ totalHoldings }} investimentos
                </span>
              </template>
            </KpiCard>
          </div>
          <div class="cell">
            <KpiCard
              data-testid="kpi"
              label="Valor atual estimado"
              :value="fmt.money(summary.totalCurrentValue, baseCurrency, { compact: true })"
            >
              <template #foot><span class="kpi-sub">posições com valor atual</span></template>
            </KpiCard>
          </div>
          <div class="cell">
            <KpiCard
              data-testid="kpi"
              label="Resultado"
              :value="fmt.moneySigned(summary.totalGain, baseCurrency, { compact: true })"
              :value-class="resultClass"
            >
              <template #foot>
                <span class="is-size-7" :class="resultClass">
                  {{ fmt.pctSigned(summary.totalGainPct ?? 0) }}
                </span>
                <span class="kpi-sub">sobre posições avaliadas</span>
              </template>
            </KpiCard>
          </div>
        </div>
      </div>

      <div class="fixed-grid has-3-cols has-1-cols-mobile">
        <div class="grid">
          <div class="cell is-col-span-2">
            <EvolutionCard :series="overviewStore.series" :currency="baseCurrency" />
          </div>
          <div class="cell">
            <AllocationCard
              :rows="kindRows"
              :total-invested="summary.totalCostBasis"
              :currency="baseCurrency"
            />
          </div>
        </div>
      </div>

      <div class="section-label">Distribuição por tipo</div>
      <div class="fixed-grid has-3-cols has-1-cols-mobile">
        <div class="grid">
          <div v-for="row in kindRows" :key="row.key" class="cell">
            <TypeSummaryCard :row="row" :currency="baseCurrency" @goto-type="gotoType" />
          </div>
        </div>
      </div>
    </template>
  </div>
</template>
