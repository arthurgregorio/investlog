<script setup lang="ts">
import NumberInput from '@/components/ui/NumberInput.vue'
import TickerBadge from '@/components/ui/TickerBadge.vue'
import { fmt } from '@/composables/useFormat'
import {
  exceedsRemaining,
  takesQuantity,
  type MoveQuantity,
} from '@/composables/useMoveHoldingsForm'
import { badgeColor } from '@/utils/walletTypes'
import type { HoldingRow } from '@/types'

defineProps<{
  holdings: HoldingRow[]
  loading: boolean
  selected: Record<string, boolean>
  quantities: Record<string, MoveQuantity>
}>()

const moveAll = defineModel<boolean>('moveAll', { required: true })

const emit = defineEmits<{
  select: [holdingId: string, checked: boolean]
  'update-quantity': [holdingId: string, quantity: MoveQuantity]
}>()

function displayName(holding: HoldingRow): string {
  return holding.ticker ?? holding.name
}
</script>

<template>
  <div class="move-list-head">
    <span class="kpi-label">Investimentos</span>
    <b-switch v-model="moveAll" :disabled="holdings.length === 0" data-testid="move-all">
      Mover tudo
    </b-switch>
  </div>

  <div class="move-list">
    <b-loading :is-full-page="false" :model-value="loading" />
    <p v-if="!loading && holdings.length === 0" class="move-hint">
      Nenhum investimento ativo nesta carteira.
    </p>
    <div
      v-for="holding in holdings"
      :key="holding.id"
      class="move-item"
      :class="{ 'is-selected': moveAll || selected[holding.id] }"
      data-testid="move-item"
    >
      <b-checkbox
        :model-value="moveAll || !!selected[holding.id]"
        :disabled="moveAll"
        @update:model-value="(checked: boolean) => emit('select', holding.id, checked)"
      />
      <TickerBadge
        :ticker="displayName(holding)"
        :color="badgeColor(holding.ticker, holding.kind)"
      />
      <div class="move-item-meta">
        <div class="t-ticker">{{ displayName(holding) }}</div>
        <div class="t-name">
          <template v-if="holding.quantity != null">
            Disponível: {{ fmt.qty(holding.quantity) }}
          </template>
          <template v-else>{{ fmt.money(holding.costBasis, holding.walletCurrency) }}</template>
        </div>
      </div>
      <b-field
        v-if="takesQuantity(holding) && selected[holding.id] && !moveAll"
        class="move-item-quantity mb-0"
        :type="exceedsRemaining(holding, quantities[holding.id]) ? 'is-danger' : ''"
        :message="exceedsRemaining(holding, quantities[holding.id]) ? 'Maior que o disponível' : ''"
      >
        <NumberInput
          :model-value="quantities[holding.id] ?? ''"
          :placeholder="`Tudo (${fmt.qty(holding.quantity ?? 0)})`"
          min="0"
          size="is-small"
          data-testid="move-quantity"
          @update:model-value="
            (quantity: MoveQuantity) => emit('update-quantity', holding.id, quantity)
          "
        />
      </b-field>
    </div>
  </div>
</template>
