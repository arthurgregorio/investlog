<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useRoute } from 'vue-router'
import AppIcon, { type IconName } from '@/components/AppIcon.vue'
import Card from '@/components/ui/Card.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import AppButton from '@/components/ui/AppButton.vue'
import InvestmentRow from '@/components/investments/InvestmentRow.vue'
import { usePortfolioStore } from '@/stores/portfolio'
import { useModals } from '@/composables/useModals'
import type { WalletKind } from '@/types'

type Filter = 'all' | WalletKind

const store = usePortfolioStore()
const { holdings } = storeToRefs(store)
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
  const f = route.query.filter
  return typeof f === 'string' && validFilters.includes(f as Filter) ? (f as Filter) : 'all'
}

const filter = ref<Filter>(filterFromRoute())
watch(() => route.query.filter, () => (filter.value = filterFromRoute()))

const rows = computed(() =>
  holdings.value.filter((h) => filter.value === 'all' || store.walletOf(h.walletId)?.type === filter.value),
)
</script>

<template>
  <div class="page">
    <div class="page-head page-head-row">
      <div>
        <div class="page-eyebrow">Logbook</div>
        <h1 class="page-title">Investimentos</h1>
      </div>
      <button class="btn btn-primary btn-md" @click="modals.openAddInvestment()">
        <AppIcon name="plus" :size="18" :stroke="2.4" />Adicionar investimento
      </button>
    </div>

    <div class="seg-tabs">
      <button
        v-for="t in tabs"
        :key="t.key"
        class="seg-tab"
        :class="{ active: t.key === filter }"
        @click="filter = t.key"
      >
        <AppIcon :name="t.icon" :size="16" />{{ t.label }}
      </button>
    </div>

    <EmptyState
      v-if="rows.length === 0"
      icon="layers"
      title="Nenhum investimento aqui"
      text="Registre uma aquisição para vê-la no seu logbook."
    >
      <template #action>
        <AppButton variant="primary" icon="plus" @click="modals.openAddInvestment()">Adicionar investimento</AppButton>
      </template>
    </EmptyState>

    <Card v-else class="table-card">
      <div class="table-scroll">
        <table class="inv-table">
          <thead>
            <tr>
              <th class="c-name">Investimento</th>
              <th>Carteira</th>
              <th class="c-num">Qtd.</th>
              <th class="c-num">Investido</th>
              <th class="c-num">Valor atual</th>
              <th class="c-num">Resultado</th>
              <th class="c-act" />
            </tr>
          </thead>
          <tbody>
            <InvestmentRow v-for="h in rows" :key="h.id" :holding="h" />
          </tbody>
        </table>
      </div>
    </Card>
  </div>
</template>
