<script setup lang="ts">
import { computed, onMounted, useTemplateRef } from 'vue'
import { useRouter } from 'vue-router'
import Card from '@/components/ui/Card.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import HoldingsTable from '@/components/investments/HoldingsTable.vue'
import InvestmentsFilterRail from '@/components/investments/InvestmentsFilterRail.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import { useHoldingsListStore } from '@/stores/holdingsList'
import { useOverviewStore } from '@/stores/overview'
import { useTypesListStore } from '@/stores/typesList'
import { useWalletsStore } from '@/stores/wallets'
import { useCurrencyStore } from '@/stores/currency'
import { useRatesStore } from '@/stores/rates'
import { useModals } from '@/composables/useModals'
import { fmt } from '@/composables/useFormat'
import { useInvestmentFilters, type InvestmentKindFilter } from '@/composables/useInvestmentFilters'
import { WALLET_TYPES } from '@/utils/walletTypes'

interface FilterChip {
  key: string
  label: string
  remove: () => void
}

const holdingsListStore = useHoldingsListStore()
const overviewStore = useOverviewStore()
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
  overviewStore.refresh()
})

const filters = useInvestmentFilters({
  onReload: ({ kind, page, ...options }) => holdingsListStore.loadKind(kind, page, options),
  onFiltersChange: () => holdingsTable.value?.closeDrawer(),
})
const {
  activeFilter,
  typeLabelFilter,
  walletIdFilter,
  searchQuery,
  hideFrozenFilter,
  sortKey,
  sortDirection,
} = filters

const kindSummaries = computed(() => overviewStore.summary?.kindSummaries ?? [])

const headline = computed(() => {
  const summary = overviewStore.summary
  if (!summary) return undefined
  const positions = kindSummaries.value.reduce((sum, kind) => sum + kind.holdingCount, 0)
  const total = fmt.money(summary.totalCurrentValue, summary.displayCurrency)
  return `${positions} posições em ${walletsStore.wallets.length} carteiras · ${total} no total`
})

const classCounts = computed(() => {
  const counts: Partial<Record<InvestmentKindFilter, number>> = {}
  for (const kind of kindSummaries.value) {
    counts[kind.kind as InvestmentKindFilter] = kind.holdingCount
  }
  if (kindSummaries.value.length > 0) {
    counts.all = kindSummaries.value.reduce((sum, kind) => sum + kind.holdingCount, 0)
  }
  return counts
})

const typeOptions = computed(() => {
  if (activeFilter.value === 'all') return []
  return typesListStore.typesForKind(activeFilter.value)
})

function walletsOfClass(filter: InvestmentKindFilter) {
  if (filter === 'all') return walletsStore.wallets
  return walletsStore.wallets.filter((wallet) => wallet.kind === filter)
}

const walletOptions = computed(() => walletsOfClass(activeFilter.value))

function selectClass(filter: InvestmentKindFilter) {
  filters.selectTab(
    filter,
    walletsOfClass(filter).map((wallet) => wallet.id),
  )
}

const chips = computed<FilterChip[]>(() => {
  const list: FilterChip[] = []
  const filter = activeFilter.value
  if (filter !== 'all') {
    list.push({
      key: `class-${filter}`,
      label: WALLET_TYPES[filter].label,
      remove: () => selectClass('all'),
    })
  }
  for (const walletId of walletIdFilter.value) {
    list.push({
      key: `wallet-${walletId}`,
      label:
        walletsStore.wallets.find((wallet) => wallet.id === walletId)?.name ??
        'Carteira selecionada',
      remove: () => filters.toggleWallet(walletId),
    })
  }
  for (const typeLabel of typeLabelFilter.value) {
    list.push({
      key: `type-${typeLabel}`,
      label: typeLabel,
      remove: () => filters.toggleType(typeLabel),
    })
  }
  if (hideFrozenFilter.value) {
    list.push({
      key: 'hide-frozen',
      label: 'Ocultar congelados',
      remove: () => filters.changeHideFrozen(false),
    })
  }
  return list
})

const activeFilterCount = computed(() => chips.value.length + (searchQuery.value.trim() ? 1 : 0))

const resultCount = computed(() =>
  holdingsListStore.totalElements === 1
    ? '1 investimento'
    : `${holdingsListStore.totalElements} investimentos`,
)

function openAddInvestment() {
  const walletId = walletIdFilter.value.length === 1 ? walletIdFilter.value[0] : undefined
  const filteredWallet = walletsStore.wallets.find((wallet) => wallet.id === walletId)
  const kind = activeFilter.value !== 'all' ? activeFilter.value : filteredWallet?.kind
  modals.openAddInvestment(kind, walletId)
}

function openReport() {
  const { href } = router.resolve({ name: 'investments-report', query: filters.reportQuery() })
  window.open(href, '_blank')
}

function onHoldingsChanged() {
  holdingsListStore.refresh()
  overviewStore.refresh()
}
</script>

<template>
  <div class="page">
    <PageHeader title="Investimentos" :description="headline">
      <b-button icon-left="file-export-outline" @click="openReport">Exportar relatório</b-button>
      <b-button type="is-primary" icon-left="plus" @click="openAddInvestment">
        Adicionar investimento
      </b-button>
    </PageHeader>

    <div class="investments-body">
      <InvestmentsFilterRail
        :active-filter="activeFilter"
        :wallet-ids="walletIdFilter"
        :type-labels="typeLabelFilter"
        :hide-frozen="hideFrozenFilter"
        :active-filter-count="activeFilterCount"
        :class-counts="classCounts"
        :wallet-options="walletOptions"
        :type-options="typeOptions"
        @select-class="selectClass"
        @toggle-wallet="filters.toggleWallet"
        @toggle-type="filters.toggleType"
        @change-hide-frozen="filters.changeHideFrozen"
        @clear="filters.clearFilters"
      />

      <Card class="table-card investments-main">
        <div class="investments-main-head">
          <b-input
            class="investments-search"
            :model-value="searchQuery"
            icon="magnify"
            type="search"
            placeholder="Buscar por ticker, nome ou segmento"
            aria-label="Buscar investimentos"
            @update:model-value="filters.changeSearch"
          />
          <b-taglist v-if="chips.length > 0" class="mb-0">
            <b-tag
              v-for="chip in chips"
              :key="chip.key"
              type="is-primary is-light"
              closable
              :aria-close-label="`Remover filtro ${chip.label}`"
              :data-testid="`chip-${chip.key}`"
              @close="chip.remove"
            >
              {{ chip.label }}
            </b-tag>
          </b-taglist>
          <span class="investments-count" data-testid="result-count">{{ resultCount }}</span>
        </div>

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

        <HoldingsTable
          v-else
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
          @holding-changed="onHoldingsChanged"
          @position-added="onHoldingsChanged"
          @relocated="onHoldingsChanged"
        />
      </Card>
    </div>
  </div>
</template>
