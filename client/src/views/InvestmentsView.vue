<script setup lang="ts">
import { computed, onMounted, ref, useTemplateRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { LocationQuery, LocationQueryRaw } from 'vue-router'
import Card from '@/components/ui/Card.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import HoldingsTable from '@/components/investments/HoldingsTable.vue'
import { useHoldingsListStore } from '@/stores/holdingsList'
import { useTypesListStore } from '@/stores/typesList'
import { useWalletsStore } from '@/stores/wallets'
import { useCurrencyStore } from '@/stores/currency'
import { useRatesStore } from '@/stores/rates'
import { useModals } from '@/composables/useModals'
import { WALLET_TYPES } from '@/utils/walletTypes'
import type { WalletKind } from '@/types'

type Filter = 'all' | WalletKind
type SortKey = 'wallet' | 'price' | 'invested' | 'current' | 'gain'

const holdingsListStore = useHoldingsListStore()
const typesListStore = useTypesListStore()
const walletsStore = useWalletsStore()
const currencyStore = useCurrencyStore()
const ratesStore = useRatesStore()
const route = useRoute()
const router = useRouter()
const modals = useModals()

const tabs: { key: Filter; label: string; icon: string }[] = [
  { key: 'all', label: 'Todos', icon: 'layers-outline' },
  { key: 'STOCKS', label: 'Ações', icon: 'trending-up' },
  { key: 'CRYPTO', label: 'Cripto', icon: 'bitcoin' },
  { key: 'FUNDS', label: 'Fundos', icon: 'office-building-outline' },
]
const validFilters: Filter[] = ['all', 'STOCKS', 'CRYPTO', 'FUNDS']
const validSortKeys: SortKey[] = ['wallet', 'price', 'invested', 'current', 'gain']
const QUERY_KEYS = ['filter', 'walletId', 'type', 'search', 'sort', 'page'] as const

function parseFilter(query: LocationQuery): Filter {
  const filterParam = query.filter
  return typeof filterParam === 'string' && validFilters.includes(filterParam as Filter)
    ? (filterParam as Filter)
    : 'all'
}

function parseSort(query: LocationQuery): { key: SortKey | null; direction: 'asc' | 'desc' } {
  const sortParam = query.sort
  if (typeof sortParam !== 'string') return { key: null, direction: 'desc' }
  const [key, direction] = sortParam.split(',')
  if (!validSortKeys.includes(key as SortKey)) return { key: null, direction: 'desc' }
  return { key: key as SortKey, direction: direction === 'asc' ? 'asc' : 'desc' }
}

function parsePage(query: LocationQuery): number {
  const pageParam = query.page
  const pageNumber = typeof pageParam === 'string' ? parseInt(pageParam, 10) : NaN
  return Number.isFinite(pageNumber) && pageNumber > 1 ? pageNumber - 1 : 0
}

function queryFingerprint(query: LocationQuery | LocationQueryRaw): string {
  return QUERY_KEYS.map((key) => `${key}=${query[key] ?? ''}`).join('&')
}

const initialSort = parseSort(route.query)

const activeFilter = ref<Filter>(parseFilter(route.query))
const typeLabelFilter = ref<string | undefined>(
  typeof route.query.type === 'string' ? route.query.type : undefined,
)
const walletIdFilter = ref<string | undefined>(
  typeof route.query.walletId === 'string' ? route.query.walletId : undefined,
)
const searchQuery = ref(typeof route.query.search === 'string' ? route.query.search : '')
const sortKey = ref<SortKey | null>(initialSort.key)
const sortDirection = ref<'asc' | 'desc'>(initialSort.direction)
const holdingsTable = useTemplateRef('holdingsTable')

// Tracks the query this component last wrote via router.replace, so the watcher below can tell
// its own self-echo apart from a real external navigation (WalletsView link, back/forward, pasted
// URL) and only re-hydrate state in the latter case — otherwise every local filter change would
// immediately wipe itself out.
let lastWrittenQueryFingerprint = queryFingerprint(route.query)

let searchDebounceHandle: ReturnType<typeof setTimeout> | undefined

const typeLabelOptions = computed(() => {
  if (activeFilter.value === 'STOCKS') return typesListStore.stockTypes
  if (activeFilter.value === 'FUNDS') return typesListStore.fundTypes
  return []
})

const walletOptions = computed(() => {
  if (activeFilter.value === 'all') return walletsStore.wallets
  return walletsStore.wallets.filter((wallet) => wallet.kind === activeFilter.value)
})

function buildQuery(pageNumber: number): LocationQueryRaw {
  const query: LocationQueryRaw = {}
  if (activeFilter.value !== 'all') query.filter = activeFilter.value
  if (walletIdFilter.value) query.walletId = walletIdFilter.value
  if (typeLabelFilter.value) query.type = typeLabelFilter.value
  const trimmedSearch = searchQuery.value.trim()
  if (trimmedSearch) query.search = trimmedSearch
  if (sortKey.value) query.sort = `${sortKey.value},${sortDirection.value}`
  if (pageNumber > 0) query.page = String(pageNumber + 1)
  return query
}

function syncQueryToRoute(pageNumber: number) {
  const query = buildQuery(pageNumber)
  lastWrittenQueryFingerprint = queryFingerprint(query)
  router.replace({ query })
}

function reload(pageNumber = 0) {
  syncQueryToRoute(pageNumber)
  holdingsListStore.loadKind(activeFilter.value, pageNumber, {
    typeLabel: typeLabelFilter.value,
    walletId: walletIdFilter.value,
    search: searchQuery.value.trim() || undefined,
    sort: sortKey.value ? `${sortKey.value},${sortDirection.value}` : undefined,
  })
}

function hydrateFiltersFromRoute(query: LocationQuery) {
  activeFilter.value = parseFilter(query)
  walletIdFilter.value = typeof query.walletId === 'string' ? query.walletId : undefined
  typeLabelFilter.value = typeof query.type === 'string' ? query.type : undefined
  searchQuery.value = typeof query.search === 'string' ? query.search : ''
  const sort = parseSort(query)
  sortKey.value = sort.key
  sortDirection.value = sort.direction
}

watch(
  () => route.query,
  (newQuery) => {
    if (queryFingerprint(newQuery) === lastWrittenQueryFingerprint) return
    hydrateFiltersFromRoute(newQuery)
    holdingsTable.value?.collapse()
    reload(parsePage(newQuery))
  },
)

onMounted(() => {
  typesListStore.load()
  walletsStore.load()
  currencyStore.load()
  ratesStore.load()
  reload(parsePage(route.query))
})

function selectTab(filter: Filter) {
  if (filter === activeFilter.value) return
  activeFilter.value = filter
  typeLabelFilter.value = undefined
  walletIdFilter.value = undefined
  searchQuery.value = ''
  holdingsTable.value?.collapse()
  reload(0)
}

function onTypeLabelChange(value: string) {
  typeLabelFilter.value = value || undefined
  holdingsTable.value?.collapse()
  reload(0)
}

function onWalletIdChange(value: string) {
  walletIdFilter.value = value || undefined
  holdingsTable.value?.collapse()
  reload(0)
}

function onSearchChange(value: string) {
  searchQuery.value = value
  if (searchDebounceHandle) clearTimeout(searchDebounceHandle)
  searchDebounceHandle = setTimeout(() => {
    holdingsTable.value?.collapse()
    reload(0)
  }, 300)
}

function toggleSort(key: string) {
  if (sortKey.value === key) {
    sortDirection.value = sortDirection.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortKey.value = key as SortKey
    sortDirection.value = 'asc'
  }
  reload(0)
}

function openAddInvestment() {
  modals.openAddInvestment(activeFilter.value !== 'all' ? activeFilter.value : undefined)
}

function openReport() {
  const query: LocationQueryRaw = {}
  if (activeFilter.value !== 'all') query.filter = activeFilter.value
  if (walletIdFilter.value) query.walletId = walletIdFilter.value
  if (typeLabelFilter.value) query.type = typeLabelFilter.value
  const trimmedSearch = searchQuery.value.trim()
  if (trimmedSearch) query.search = trimmedSearch

  const { href } = router.resolve({ name: 'investments-report', query })
  window.open(href, '_blank')
}
</script>

<template>
  <div class="page">
    <div class="page-head">
      <h1 class="page-title">Investimentos</h1>
      <p class="page-desc">Aqui você gerencia seus investimentos</p>
    </div>

    <div class="inv-controls">
      <div class="seg-tabs">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          class="seg-tab"
          :class="{ active: tab.key === activeFilter }"
          @click="selectTab(tab.key)"
        >
          <b-icon :icon="tab.icon" size="is-small" />{{ tab.label }}
        </button>
      </div>

      <div class="inv-toolbar">
        <b-select
          v-if="walletOptions.length > 0"
          :model-value="walletIdFilter ?? ''"
          @update:model-value="onWalletIdChange"
        >
          <option value="">Todas as carteiras</option>
          <option v-for="wallet in walletOptions" :key="wallet.id" :value="wallet.id">
            [{{ WALLET_TYPES[wallet.kind].label }}] {{ wallet.name }}
          </option>
        </b-select>
        <b-select
          v-if="typeLabelOptions.length > 0"
          :model-value="typeLabelFilter ?? ''"
          @update:model-value="onTypeLabelChange"
        >
          <option value="">Todos os tipos</option>
          <option v-for="assetType in typeLabelOptions" :key="assetType.id" :value="assetType.name">
            {{ assetType.name }}
          </option>
        </b-select>
        <b-input
          class="search-input"
          :model-value="searchQuery"
          icon="magnify"
          placeholder="Buscar por nome ou ticker"
          @update:model-value="onSearchChange"
        />
        <b-button type="is-primary" icon-left="plus" @click="openAddInvestment">
          Adicionar investimento
        </b-button>
        <b-tooltip label="Exportar" position="is-top">
          <b-button
            icon-left="file-export-outline"
            aria-label="Exportar relatório"
            @click="openReport"
          />
        </b-tooltip>
      </div>
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
        @sort="toggleSort"
        @page-change="reload"
        @holding-changed="holdingsListStore.refresh()"
        @position-added="holdingsListStore.refresh()"
        @relocated="holdingsListStore.refresh()"
      />
    </Card>
  </div>
</template>
