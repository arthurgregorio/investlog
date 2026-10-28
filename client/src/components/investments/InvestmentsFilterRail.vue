<script setup lang="ts">
import { computed } from 'vue'
import type { InvestmentKindFilter } from '@/composables/useInvestmentFilters'
import { WALLET_TYPES } from '@/utils/walletTypes'
import type { AssetType, WalletResponse } from '@/types'

const props = defineProps<{
  activeFilter: InvestmentKindFilter
  walletIds: readonly string[]
  typeLabels: readonly string[]
  hideFrozen: boolean
  activeFilterCount: number
  classCounts: Partial<Record<InvestmentKindFilter, number>>
  walletOptions: WalletResponse[]
  typeOptions: AssetType[]
}>()

const emit = defineEmits<{
  'select-class': [filter: InvestmentKindFilter]
  'toggle-wallet': [walletId: string]
  'toggle-type': [typeLabel: string]
  'change-hide-frozen': [value: boolean]
  clear: []
}>()

const CLASSES: { value: InvestmentKindFilter; label: string }[] = [
  { value: 'all', label: 'Todos os tipos' },
  { value: 'STOCKS', label: 'Ações' },
  { value: 'CRYPTO', label: 'Cripto' },
  { value: 'FUNDS', label: 'Fundos' },
]

const typeHint = computed(() =>
  props.activeFilter === 'all'
    ? 'Escolha um tipo de carteira para filtrar por tipo de ativo.'
    : 'Nenhum tipo de ativo cadastrado para este tipo de carteira.',
)

function classDot(filter: InvestmentKindFilter) {
  return filter === 'all' ? 'var(--text-2)' : WALLET_TYPES[filter].accent
}
</script>

<template>
  <aside class="card filter-rail" aria-label="Filtros">
    <div class="is-flex is-justify-content-space-between is-align-items-center px-4 py-3">
      <h2 class="is-flex is-align-items-center is-gap-1 has-text-weight-bold">
        Filtros
        <span class="rail-count" data-testid="active-filter-count">{{ activeFilterCount }}</span>
      </h2>
      <b-button type="is-ghost" size="is-small" data-testid="clear-filters" @click="emit('clear')">
        Limpar
      </b-button>
    </div>

    <fieldset class="rail-group">
      <legend class="is-sr-only">Tipo de carteira</legend>
      <p class="rail-title" aria-hidden="true">Tipo de carteira</p>
      <label
        v-for="option in CLASSES"
        :key="option.value"
        class="rail-option"
        :class="{ 'is-selected': activeFilter === option.value }"
      >
        <input
          type="radio"
          name="investments-class"
          :value="option.value"
          :checked="activeFilter === option.value"
          :data-testid="`class-${option.value}`"
          @change="emit('select-class', option.value)"
        />
        <span class="rail-dot" :style="{ background: classDot(option.value) }" />
        <span class="rail-label">{{ option.label }}</span>
        <span v-if="classCounts[option.value] != null" class="rail-side">{{
          classCounts[option.value]
        }}</span>
      </label>
    </fieldset>

    <fieldset class="rail-group">
      <legend class="is-sr-only">Carteiras</legend>
      <p class="rail-title" aria-hidden="true">Carteiras</p>
      <label v-for="wallet in walletOptions" :key="wallet.id" class="rail-option">
        <input
          type="checkbox"
          :checked="walletIds.includes(wallet.id)"
          :data-testid="`wallet-${wallet.id}`"
          @change="emit('toggle-wallet', wallet.id)"
        />
        <span class="rail-dot" :style="{ background: WALLET_TYPES[wallet.kind].accent }" />
        <span class="rail-label">{{ wallet.name }}</span>
        <span class="rail-side">{{ wallet.holdingCount }}</span>
      </label>
    </fieldset>

    <fieldset class="rail-group">
      <legend class="is-sr-only">Tipo de ativo</legend>
      <p class="rail-title" aria-hidden="true">Tipo de ativo</p>
      <template v-if="typeOptions.length > 0">
        <label v-for="assetType in typeOptions" :key="assetType.id" class="rail-option">
          <input
            type="checkbox"
            :checked="typeLabels.includes(assetType.name)"
            :data-testid="`type-${assetType.name}`"
            @change="emit('toggle-type', assetType.name)"
          />
          <span class="rail-label">{{ assetType.name }}</span>
        </label>
      </template>
      <p v-else class="rail-hint" data-testid="type-hint">{{ typeHint }}</p>
    </fieldset>

    <fieldset class="rail-group">
      <legend class="is-sr-only">Situação</legend>
      <p class="rail-title" aria-hidden="true">Situação</p>
      <label class="rail-option">
        <input
          type="checkbox"
          :checked="hideFrozen"
          data-testid="hide-frozen"
          @change="emit('change-hide-frozen', ($event.target as HTMLInputElement).checked)"
        />
        <span class="rail-label">Ocultar congelados</span>
      </label>
    </fieldset>
  </aside>
</template>

<style scoped>
.rail-count {
  display: inline-grid;
  place-items: center;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 10px;
  background: var(--tag-bg);
  color: var(--text-2);
  font-size: 11.5px;
  font-weight: 700;
}

.rail-hint {
  padding: 4px 8px;
  font-size: 12.5px;
  color: var(--text-muted);
}
</style>
