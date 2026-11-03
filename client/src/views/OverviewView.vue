<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/ui/PageHeader.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import OverviewHealthCard from '@/components/overview/OverviewHealthCard.vue'
import EvolutionCard from '@/components/overview/EvolutionCard.vue'
import ThisMonthCard from '@/components/overview/ThisMonthCard.vue'
import TypeSummaryCard from '@/components/overview/TypeSummaryCard.vue'
import CashSummaryCard from '@/components/overview/CashSummaryCard.vue'
import { useOverviewStore } from '@/stores/overview'
import { useWalletsStore } from '@/stores/wallets'
import { useModals } from '@/composables/useModals'
import { walletKindRows } from '@/utils/walletKindRows'
import type { WalletKind } from '@/types'

const overviewStore = useOverviewStore()
const walletsStore = useWalletsStore()
const router = useRouter()
const { openCreateWallet } = useModals()

onMounted(() => {
  Promise.all([overviewStore.refresh(), walletsStore.load()])
})

const summary = computed(() => overviewStore.summary)
const baseCurrency = computed(() => summary.value?.displayCurrency ?? 'BRL')
const kindRows = computed(() =>
  walletKindRows(summary.value?.kindSummaries ?? [], walletsStore.wallets),
)
const hasNothing = computed(
  () =>
    !!summary.value &&
    summary.value.kindSummaries.length === 0 &&
    summary.value.totalCash === 0 &&
    overviewStore.series.length === 0,
)

function gotoType(kind: WalletKind) {
  router.push({ name: 'investments', query: { filter: kind } })
}
</script>

<template>
  <div class="page">
    <b-loading :is-full-page="false" :model-value="overviewStore.loading" />

    <PageHeader
      title="Visão geral"
      description="Quanto você colocou, quanto rendeu e onde está o seu dinheiro"
    />

    <template v-if="summary">
      <EmptyState
        v-if="hasNothing"
        icon="chart-areaspline"
        title="Nada para resumir ainda"
        text="Assim que você criar uma carteira e registrar um investimento, esta página mostra quanto você colocou, quanto rendeu e onde está o dinheiro."
      >
        <template #action>
          <b-button type="is-primary" icon-left="plus" class="mt-4" @click="openCreateWallet()"
            >Criar carteira</b-button
          >
        </template>
      </EmptyState>

      <template v-else>
        <OverviewHealthCard
          :summary="summary"
          :currency="baseCurrency"
          @view-results="router.push({ name: 'overview-results' })"
          @view-investments="router.push({ name: 'investments' })"
        />

        <div class="fixed-grid has-3-cols has-1-cols-mobile">
          <div class="grid">
            <div class="cell is-col-span-2">
              <EvolutionCard :series="overviewStore.series" :currency="baseCurrency" />
            </div>
            <div v-if="overviewStore.month" class="cell">
              <ThisMonthCard
                :month="overviewStore.month"
                :unpriced-count="summary.unpricedHoldingCount"
                :currency="baseCurrency"
                @view-investments="router.push({ name: 'investments' })"
              />
            </div>
          </div>
        </div>

        <div class="section-label">Onde está o dinheiro</div>
        <div class="fixed-grid has-1-cols-mobile has-2-cols-tablet has-4-cols-desktop">
          <div class="grid">
            <div v-for="row in kindRows" :key="row.key" class="cell">
              <TypeSummaryCard
                :row="row"
                :currency="baseCurrency"
                @goto-type="gotoType"
                @create-wallet="openCreateWallet"
              />
            </div>
            <div class="cell">
              <CashSummaryCard
                :cash="summary.totalCash"
                :wallet-count="summary.cashWalletCount"
                :currency="baseCurrency"
                @view="router.push({ name: 'wallets' })"
              />
            </div>
          </div>
        </div>
      </template>
    </template>
  </div>
</template>
