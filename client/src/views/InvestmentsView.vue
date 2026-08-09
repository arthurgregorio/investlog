<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Card from '@/components/ui/Card.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import TickerBadge from '@/components/ui/TickerBadge.vue'
import GainChip from '@/components/ui/GainChip.vue'
import SortTh from '@/components/ui/SortTh.vue'
import HoldingDetailPanel from '@/components/investments/HoldingDetailPanel.vue'
import { useHoldingsListStore } from '@/stores/holdingsList'
import { useTypesListStore } from '@/stores/typesList'
import { useWalletsStore } from '@/stores/wallets'
import { useCurrencyStore } from '@/stores/currency'
import { useRatesStore } from '@/stores/rates'
import { useModals } from '@/composables/useModals'
import { fmt } from '@/composables/useFormat'
import { badgeColor, WALLET_TYPES } from '@/utils/walletTypes'
import type { HoldingRow, WalletKind } from '@/types'

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

function filterFromRoute(): Filter {
  const filterParam = route.query.filter
  return typeof filterParam === 'string' && validFilters.includes(filterParam as Filter)
    ? (filterParam as Filter)
    : 'all'
}

const activeFilter = ref<Filter>(filterFromRoute())
const openedDetails = ref<HoldingRow[]>([])
const typeLabelFilter = ref<string | undefined>(undefined)
const walletIdFilter = ref<string | undefined>(undefined)
const searchQuery = ref('')
const sortKey = ref<SortKey | null>(null)
const sortDirection = ref<'asc' | 'desc'>('desc')

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

function reload(pageNumber = 0) {
  holdingsListStore.loadKind(activeFilter.value, pageNumber, {
    typeLabel: typeLabelFilter.value,
    walletId: walletIdFilter.value,
    search: searchQuery.value.trim() || undefined,
    sort: sortKey.value ? `${sortKey.value},${sortDirection.value}` : undefined,
  })
}

watch(
  () => route.query.filter,
  () => {
    activeFilter.value = filterFromRoute()
    typeLabelFilter.value = undefined
    walletIdFilter.value = undefined
    searchQuery.value = ''
    openedDetails.value = []
    reload(0)
  },
)

onMounted(() => {
  typesListStore.load()
  walletsStore.load()
  currencyStore.load()
  ratesStore.load()
  reload(0)
})

function selectTab(filter: Filter) {
  if (filter === activeFilter.value) return
  router.replace({ query: { ...route.query, filter: filter === 'all' ? undefined : filter } })
}

function onTypeLabelChange(value: string) {
  typeLabelFilter.value = value || undefined
  openedDetails.value = []
  reload(0)
}

function onWalletIdChange(value: string) {
  walletIdFilter.value = value || undefined
  openedDetails.value = []
  reload(0)
}

function onSearchChange(value: string) {
  searchQuery.value = value
  if (searchDebounceHandle) clearTimeout(searchDebounceHandle)
  searchDebounceHandle = setTimeout(() => {
    openedDetails.value = []
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

function onPageChange(newPage: number) {
  openedDetails.value = []
  reload(newPage - 1)
}

function toggleRow(row: HoldingRow) {
  const alreadyOpen = openedDetails.value.some((openRow) => openRow.id === row.id)
  openedDetails.value = alreadyOpen ? [] : [row]
}

function isOpen(row: HoldingRow): boolean {
  return openedDetails.value.some((openRow) => openRow.id === row.id)
}

function onHoldingDeleted() {
  openedDetails.value = []
  holdingsListStore.refresh()
}

function displayName(row: HoldingRow): string {
  return row.ticker ?? row.name
}

function subLabel(row: HoldingRow): string {
  if (row.kind === 'FUNDS') return row.typeLabel ?? 'Fundo'
  if (row.kind === 'CRYPTO') return 'Cripto'
  return row.typeLabel ?? 'Ação'
}

function openAddInvestment() {
  modals.openAddInvestment(activeFilter.value !== 'all' ? activeFilter.value : undefined)
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
        <b-button
          type="is-primary"
          class="has-text-light toolbar-add"
          icon-left="plus"
          @click="openAddInvestment"
        >
          Adicionar investimento
        </b-button>
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
        <b-button
          type="is-primary"
          class="has-text-light"
          icon-left="plus"
          @click="openAddInvestment"
        >
          Adicionar investimento
        </b-button>
      </template>
    </EmptyState>

    <Card v-else class="table-card">
      <div class="table-wrap">
        <b-loading :is-full-page="false" :active="holdingsListStore.loading" />
        <div class="table-scroll">
          <table class="inv-table">
            <thead>
              <tr>
                <th>Investimento</th>
                <SortTh
                  sort-key="wallet"
                  :active-key="sortKey"
                  :direction="sortDirection"
                  align="left"
                  @toggle="toggleSort"
                  >Carteira</SortTh
                >
                <th class="c-num">Qtd.</th>
                <SortTh
                  sort-key="price"
                  :active-key="sortKey"
                  :direction="sortDirection"
                  @toggle="toggleSort"
                  >Preço atual</SortTh
                >
                <SortTh
                  sort-key="invested"
                  :active-key="sortKey"
                  :direction="sortDirection"
                  @toggle="toggleSort"
                  >Investido</SortTh
                >
                <SortTh
                  sort-key="current"
                  :active-key="sortKey"
                  :direction="sortDirection"
                  @toggle="toggleSort"
                  >Valor atual</SortTh
                >
                <SortTh
                  sort-key="gain"
                  :active-key="sortKey"
                  :direction="sortDirection"
                  @toggle="toggleSort"
                  >Resultado</SortTh
                >
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
                          <span class="type-tag" :class="`tt-${row.kind.toLowerCase()}`">{{
                            subLabel(row)
                          }}</span>
                        </div>
                        <div v-if="row.kind !== 'FUNDS' && row.name" class="t-name">
                          {{ row.name }}
                        </div>
                      </div>
                    </div>
                  </td>
                  <td>
                    <span class="wallet-ref">
                      <span
                        class="wref-dot"
                        :style="{ background: WALLET_TYPES[row.kind].accent }"
                      />
                      {{ row.walletName }}
                    </span>
                  </td>
                  <td class="c-num">{{ row.quantity == null ? '—' : fmt.qty(row.quantity) }}</td>
                  <td class="c-num">
                    <template v-if="row.kind !== 'FUNDS' && row.currentPrice != null">
                      {{
                        fmt.money(
                          currencyStore.convert(row.currentPrice, row.walletCurrency),
                          currencyStore.displayCurrency,
                        )
                      }}
                    </template>
                    <template v-else-if="row.kind === 'FUNDS' && row.currentValue != null">
                      {{
                        fmt.money(
                          currencyStore.convert(row.currentValue, row.walletCurrency),
                          currencyStore.displayCurrency,
                        )
                      }}
                    </template>
                    <span v-else class="gl-empty">—</span>
                  </td>
                  <td class="c-num">
                    <div class="cell-strong">
                      {{
                        fmt.money(
                          currencyStore.convert(row.costBasis, row.walletCurrency),
                          currencyStore.displayCurrency,
                        )
                      }}
                    </div>
                  </td>
                  <td class="c-num">
                    <span v-if="row.currentValue == null" class="gl-empty">—</span>
                    <template v-else>
                      {{
                        fmt.money(
                          currencyStore.convert(row.currentValue, row.walletCurrency),
                          currencyStore.displayCurrency,
                        )
                      }}
                    </template>
                  </td>
                  <td class="c-num">
                    <GainChip
                      :value="
                        row.gain == null
                          ? null
                          : currencyStore.convert(row.gain, row.walletCurrency)
                      "
                      :pct="row.gainPct"
                      :cur="currencyStore.displayCurrency"
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
                  <td colspan="8">
                    <HoldingDetailPanel
                      :row="row"
                      @deleted="onHoldingDeleted"
                      @position-added="holdingsListStore.refresh()"
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
  </div>
</template>
