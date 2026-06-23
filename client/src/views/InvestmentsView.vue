<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon, { type IconName } from '@/components/AppIcon.vue'
import Card from '@/components/ui/Card.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import TickerBadge from '@/components/ui/TickerBadge.vue'
import GainChip from '@/components/ui/GainChip.vue'
import HoldingDetailPanel from '@/components/investments/HoldingDetailPanel.vue'
import { useHoldingsListStore } from '@/stores/holdingsList'
import { useModals } from '@/composables/useModals'
import { fmt } from '@/composables/useFormat'
import { WALLET_TYPES, badgeColor } from '@/utils/walletTypes'
import type { HoldingRow, WalletKind } from '@/types'

type Filter = 'all' | WalletKind

const holdingsListStore = useHoldingsListStore()
const route = useRoute()
const router = useRouter()
const modals = useModals()

const tabs: { key: Filter; label: string; icon: IconName }[] = [
  { key: 'all', label: 'Todos', icon: 'layers' },
  { key: 'STOCKS', label: 'Ações', icon: 'trendUp' },
  { key: 'CRYPTO', label: 'Cripto', icon: 'coins' },
  { key: 'FUNDS', label: 'Fundos', icon: 'building' },
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

watch(() => route.query.filter, () => {
  activeFilter.value = filterFromRoute()
  openedDetails.value = []
  holdingsListStore.loadKind(activeFilter.value, 0)
})

onMounted(() => holdingsListStore.loadKind(activeFilter.value, 0))

function selectTab(filter: Filter) {
  if (filter === activeFilter.value) return
  router.replace({ query: { ...route.query, filter: filter === 'all' ? undefined : filter } })
}

function onPageChange(newPage: number) {
  openedDetails.value = []
  holdingsListStore.loadKind(activeFilter.value, newPage - 1)
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
</script>

<template>
  <div class="page">
    <div class="page-head page-head-row">
      <div>
        <h1 class="page-title">Investimentos</h1>
        <p class="page-desc">Aqui você gerencia seus investimentos</p>
      </div>
      <b-button type="is-primary" class="has-text-light" icon-left="plus" @click="modals.openAddInvestment()">
        Adicionar investimento
      </b-button>
    </div>

    <div class="seg-tabs">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        class="seg-tab"
        :class="{ active: tab.key === activeFilter }"
        @click="selectTab(tab.key)"
      >
        <AppIcon :name="tab.icon" :size="16" />{{ tab.label }}
      </button>
    </div>

    <EmptyState
      v-if="holdingsListStore.loaded && !holdingsListStore.loading && holdingsListStore.rows.length === 0"
      icon="layers"
      title="Nenhum investimento aqui"
      text="Registre uma aquisição para vê-la no seu logbook."
    >
      <template #action>
        <b-button type="is-primary" class="has-text-light" icon-left="plus" @click="modals.openAddInvestment()">
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
                <th>Carteira</th>
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
                <tr
                  class="inv-row"
                  :class="{ 'is-open': isOpen(row) }"
                  @click="toggleRow(row)"
                >
                  <td>
                    <div class="name-cell">
                      <TickerBadge :ticker="displayName(row)" :color="badgeColor(row.ticker, row.kind)" />
                      <div class="name-meta">
                        <div class="name-line">
                          <span class="t-ticker">{{ displayName(row) }}</span>
                          <span class="type-tag" :class="`tt-${row.kind.toLowerCase()}`">{{ subLabel(row) }}</span>
                        </div>
                        <div v-if="row.kind !== 'FUNDS' && row.name" class="t-name">{{ row.name }}</div>
                      </div>
                    </div>
                  </td>
                  <td>
                    <span class="wallet-ref">
                      <span class="wref-dot" :style="{ background: WALLET_TYPES[row.kind].accent }" />
                      {{ row.walletName }}
                    </span>
                  </td>
                  <td class="c-num">{{ row.quantity == null ? '—' : fmt.qty(row.quantity) }}</td>
                  <td class="c-num">
                    <template v-if="row.kind !== 'FUNDS' && row.currentPrice != null">{{ fmt.money(row.currentPrice, row.walletCurrency) }}</template>
                    <template v-else-if="row.kind === 'FUNDS' && row.currentValue != null">{{ fmt.money(row.currentValue, row.walletCurrency) }}</template>
                    <span v-else class="gl-empty">—</span>
                  </td>
                  <td class="c-num">
                    <div class="cell-strong">{{ fmt.money(row.costBasis, row.walletCurrency) }}</div>
                  </td>
                  <td class="c-num">
                    <span v-if="row.currentValue == null" class="gl-empty">—</span>
                    <template v-else>{{ fmt.money(row.currentValue, row.walletCurrency) }}</template>
                  </td>
                  <td class="c-num">
                    <GainChip :value="row.gain" :pct="row.gainPct" :cur="row.walletCurrency" />
                  </td>
                  <td class="c-act">
                    <span class="chev">
                      <AppIcon :name="isOpen(row) ? 'chevronUp' : 'chevronDown'" :size="18" />
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
