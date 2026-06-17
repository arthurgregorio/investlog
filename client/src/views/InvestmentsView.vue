<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
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
const modals = useModals()

const tabs: { key: Filter; label: string; icon: IconName }[] = [
  { key: 'all', label: 'Todos', icon: 'layers' },
  { key: 'stocks', label: 'Ações', icon: 'trendUp' },
  { key: 'crypto', label: 'Cripto', icon: 'coins' },
  { key: 'funds', label: 'Fundos', icon: 'building' },
]
const validFilters: Filter[] = ['all', 'stocks', 'crypto', 'funds']

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
  activeFilter.value = filter
  openedDetails.value = []
  holdingsListStore.loadKind(filter, 0)
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
  if (row.kind === 'funds') return row.typeLabel ?? 'Fundo'
  if (row.kind === 'crypto') return 'Cripto'
  return row.typeLabel ?? 'Ação'
}
</script>

<template>
  <div class="page">
    <div class="page-head page-head-row">
      <div>
        <div class="page-eyebrow">Logbook</div>
        <h1 class="page-title">Investimentos</h1>
      </div>
      <b-button type="is-primary" icon-left="plus" @click="modals.openAddInvestment()">
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
        <b-button type="is-primary" icon-left="plus" @click="modals.openAddInvestment()">
          Adicionar investimento
        </b-button>
      </template>
    </EmptyState>

    <Card v-else class="table-card">
      <b-table
        :data="holdingsListStore.rows"
        :loading="holdingsListStore.loading"
        backend-pagination
        :total="holdingsListStore.totalElements"
        :per-page="holdingsListStore.pageSize"
        :current-page="holdingsListStore.page + 1"
        paginated
        pagination-simple
        hoverable
        detailed
        :show-detail-icon="false"
        detail-key="id"
        :opened-detailed="openedDetails"
        @page-change="onPageChange"
        @click="(row: HoldingRow) => toggleRow(row)"
      >
        <b-table-column v-slot="{ row }" label="Investimento" cell-class="c-name">
          <div class="name-cell">
            <TickerBadge :ticker="displayName(row)" :color="badgeColor(row.ticker, row.kind)" />
            <div class="name-meta">
              <div class="name-line">
                <span class="t-ticker">{{ displayName(row) }}</span>
                <span class="type-tag" :class="`tt-${row.kind}`">{{ subLabel(row) }}</span>
              </div>
              <div v-if="row.kind !== 'funds' && row.name" class="t-name">{{ row.name }}</div>
            </div>
          </div>
        </b-table-column>

        <b-table-column v-slot="{ row }" label="Carteira">
          <span class="wallet-ref">
            <span class="wref-dot" :style="{ background: WALLET_TYPES[row.kind].accent }" />
            {{ row.walletName }}
          </span>
        </b-table-column>

        <b-table-column v-slot="{ row }" label="Qtd." cell-class="c-num">
          {{ row.quantity == null ? '—' : fmt.qty(row.quantity) }}
        </b-table-column>

        <b-table-column v-slot="{ row }" label="Investido" cell-class="c-num">
          <div class="cell-strong">{{ fmt.money(row.costBasis, row.walletCurrency) }}</div>
        </b-table-column>

        <b-table-column v-slot="{ row }" label="Valor atual" cell-class="c-num">
          <span v-if="row.currentValue == null" class="gl-empty">—</span>
          <template v-else>{{ fmt.money(row.currentValue, row.walletCurrency) }}</template>
        </b-table-column>

        <b-table-column v-slot="{ row }" label="Resultado" cell-class="c-num">
          <GainChip :value="row.gain" :pct="row.gainPct" :cur="row.walletCurrency" />
        </b-table-column>

        <b-table-column v-slot="{ row }" cell-class="c-act">
          <span class="chev">
            <AppIcon :name="isOpen(row) ? 'chevronUp' : 'chevronDown'" :size="18" />
          </span>
        </b-table-column>

        <template #detail="{ row }">
          <HoldingDetailPanel
            :row="row"
            @deleted="onHoldingDeleted"
            @position-added="holdingsListStore.refresh()"
          />
        </template>
      </b-table>
    </Card>
  </div>
</template>
