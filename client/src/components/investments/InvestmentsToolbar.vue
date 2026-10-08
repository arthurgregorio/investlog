<script setup lang="ts">
import SegmentedTabs from '@/components/ui/SegmentedTabs.vue'
import type { InvestmentKindFilter } from '@/composables/useInvestmentFilters'
import { WALLET_TYPES } from '@/utils/walletTypes'
import type { AssetType, WalletResponse } from '@/types'

defineProps<{
  activeFilter: InvestmentKindFilter
  walletId?: string
  typeLabel?: string
  search: string
  walletOptions: WalletResponse[]
  typeLabelOptions: AssetType[]
}>()

const emit = defineEmits<{
  'select-tab': [filter: InvestmentKindFilter]
  'change-wallet': [walletId: string]
  'change-type': [typeLabel: string]
  'change-search': [search: string]
  'add-investment': []
  export: []
}>()

const KIND_TABS: { value: InvestmentKindFilter; label: string; icon: string }[] = [
  { value: 'all', label: 'Todos', icon: 'layers-outline' },
  { value: 'STOCKS', label: 'Ações', icon: 'trending-up' },
  { value: 'CRYPTO', label: 'Cripto', icon: 'bitcoin' },
  { value: 'FUNDS', label: 'Fundos', icon: 'office-building-outline' },
]
</script>

<template>
  <div class="inv-controls">
    <SegmentedTabs
      :model-value="activeFilter"
      :options="KIND_TABS"
      list-label="Tipo de investimento"
      @update:model-value="emit('select-tab', $event)"
    />

    <div class="inv-toolbar">
      <b-select
        v-if="walletOptions.length > 0"
        :model-value="walletId ?? ''"
        @update:model-value="emit('change-wallet', $event)"
      >
        <option value="">Todas as carteiras</option>
        <option v-for="wallet in walletOptions" :key="wallet.id" :value="wallet.id">
          [{{ WALLET_TYPES[wallet.kind].label }}] {{ wallet.name }}
        </option>
      </b-select>
      <b-select
        v-if="typeLabelOptions.length > 0"
        :model-value="typeLabel ?? ''"
        @update:model-value="emit('change-type', $event)"
      >
        <option value="">Todos os tipos</option>
        <option v-for="assetType in typeLabelOptions" :key="assetType.id" :value="assetType.name">
          {{ assetType.name }}
        </option>
      </b-select>
      <b-input
        class="search-input"
        :model-value="search"
        icon="magnify"
        placeholder="Buscar por nome ou ticker"
        @update:model-value="emit('change-search', $event)"
      />
      <b-button type="is-primary" icon-left="plus" @click="emit('add-investment')">
        Adicionar investimento
      </b-button>
      <b-tooltip label="Exportar" position="is-top">
        <b-button
          icon-left="file-export-outline"
          aria-label="Exportar relatório"
          @click="emit('export')"
        />
      </b-tooltip>
    </div>
  </div>
</template>

<style scoped>
.search-input {
  max-width: 280px;
}

@media (width <= 680px) {
  .segmented-tabs {
    justify-content: space-between;
  }

  .search-input {
    flex: 1 1 160px;
    max-width: none;
  }
}
</style>
