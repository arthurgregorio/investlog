<script setup lang="ts">
import { computed, onMounted, useTemplateRef } from 'vue'
import { useRouter } from 'vue-router'
import Card from '@/components/ui/Card.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import HoldingsTable from '@/components/investments/HoldingsTable.vue'
import InvestmentsToolbar from '@/components/investments/InvestmentsToolbar.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import { useHoldingsListStore } from '@/stores/holdingsList'
import { useTypesListStore } from '@/stores/typesList'
import { useWalletsStore } from '@/stores/wallets'
import { useCurrencyStore } from '@/stores/currency'
import { useRatesStore } from '@/stores/rates'
import { useModals } from '@/composables/useModals'
import { useInvestmentFilters } from '@/composables/useInvestmentFilters'

const holdingsListStore = useHoldingsListStore()
const typesListStore = useTypesListStore()
const walletsStore = useWalletsStore()
const currencyStore = useCurrencyStore()
const ratesStore = useRatesStore()
const router = useRouter()
const modals = useModals()
const holdingsTable = useTemplateRef('holdingsTable')

onMounted(() => {
  typesListStore.load()
  walletsStore.load()
  currencyStore.load()
  ratesStore.load()
})

const filters = useInvestmentFilters({
  onReload: ({ kind, page, ...options }) => holdingsListStore.loadKind(kind, page, options),
  onFiltersChange: () => holdingsTable.value?.collapse(),
})
const { activeFilter, typeLabelFilter, walletIdFilter, searchQuery, sortKey, sortDirection } =
  filters

const typeLabelOptions = computed(() => {
  if (activeFilter.value === 'STOCKS') return typesListStore.stockTypes
  if (activeFilter.value === 'FUNDS') return typesListStore.fundTypes
  return []
})

const walletOptions = computed(() => {
  if (activeFilter.value === 'all') return walletsStore.wallets
  return walletsStore.wallets.filter((wallet) => wallet.kind === activeFilter.value)
})

function openAddInvestment() {
  modals.openAddInvestment(activeFilter.value !== 'all' ? activeFilter.value : undefined)
}

function openReport() {
  const { href } = router.resolve({ name: 'investments-report', query: filters.reportQuery() })
  window.open(href, '_blank')
}
</script>

<template>
  <div class="page">
    <PageHeader title="Investimentos" description="Aqui você gerencia seus investimentos" />

    <InvestmentsToolbar
      :active-filter="activeFilter"
      :wallet-id="walletIdFilter"
      :type-label="typeLabelFilter"
      :search="searchQuery"
      :wallet-options="walletOptions"
      :type-label-options="typeLabelOptions"
      @select-tab="filters.selectTab"
      @change-wallet="filters.changeWallet"
      @change-type="filters.changeType"
      @change-search="filters.changeSearch"
      @add-investment="openAddInvestment"
      @export="openReport"
    />

    <EmptyState
      v-if="
        holdingsListStore.loaded &&
        !holdingsListStore.loading &&
        holdingsListStore.rows.length === 0
      "
      icon="layers-outline"
      title="Nenhum investimento aqui"
      text="Registre uma aquisição para vê-la no seu logbook."
    >
      <template #action>
        <b-button type="is-primary" icon-left="plus" @click="openAddInvestment">
          Adicionar investimento
        </b-button>
      </template>
    </EmptyState>

    <Card v-else class="table-card">
      <HoldingsTable
        ref="holdingsTable"
        :rows="holdingsListStore.rows"
        :loading="holdingsListStore.loading"
        :page="holdingsListStore.page"
        :page-size="holdingsListStore.pageSize"
        :total-elements="holdingsListStore.totalElements"
        show-wallet-column
        convert-to-display-currency
        sortable
        :sort-key="sortKey"
        :sort-direction="sortDirection"
        @sort="filters.toggleSort"
        @page-change="filters.reload"
        @holding-changed="holdingsListStore.refresh()"
        @position-added="holdingsListStore.refresh()"
        @relocated="holdingsListStore.refresh()"
      />
    </Card>
  </div>
</template>
