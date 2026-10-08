<script setup lang="ts">
import { computed } from 'vue'
import FrozenBadge from '@/components/ui/FrozenBadge.vue'
import GainChip from '@/components/ui/GainChip.vue'
import TickerBadge from '@/components/ui/TickerBadge.vue'
import { useDisplayMoney, type DisplayAmount } from '@/composables/useDisplayMoney'
import { fmt } from '@/composables/useFormat'
import { badgeColor, WALLET_TYPES } from '@/utils/walletTypes'
import type { HoldingRow } from '@/types'

const props = defineProps<{
  row: HoldingRow
  expanded: boolean
  showWalletColumn: boolean
  convertToDisplayCurrency: boolean
}>()
const emit = defineEmits<{ toggle: [] }>()

const { toDisplayCurrency } = useDisplayMoney()

const displayName = computed(() => props.row.ticker ?? props.row.name)

const subLabel = computed(() => {
  if (props.row.kind === 'FUNDS') return props.row.typeLabel ?? 'Fundo'
  if (props.row.kind === 'CRYPTO') return 'Cripto'
  return props.row.typeLabel ?? 'Ação'
})

const priceAmount = computed(() =>
  props.row.kind === 'FUNDS' ? props.row.currentValue : props.row.currentPrice,
)

const averagePrice = computed(() =>
  props.row.kind !== 'FUNDS' && props.row.quantity
    ? props.row.costBasis / props.row.quantity
    : null,
)

const gain = computed(() => (props.row.gain == null ? null : shown(props.row.gain)))

function shown(amount: number): DisplayAmount {
  return props.convertToDisplayCurrency
    ? toDisplayCurrency(amount, props.row.walletCurrency)
    : { amount, currency: props.row.walletCurrency }
}

function money(amount: number): string {
  const displayAmount = shown(amount)
  return fmt.money(displayAmount.amount, displayAmount.currency)
}
</script>

<template>
  <tr
    class="inv-row"
    :class="{ 'is-open': expanded, 'is-frozen': row.frozen }"
    @click="emit('toggle')"
  >
    <td>
      <div class="is-flex is-align-items-center is-gap-1.5">
        <FrozenBadge v-if="row.frozen" />
        <TickerBadge v-else :ticker="displayName" :color="badgeColor(row.ticker, row.kind)" />
        <div class="name-meta">
          <div class="is-flex is-align-items-center is-gap-1">
            <span class="t-ticker">{{ displayName }}</span>
            <span class="type-tag" :class="`tt-${row.kind.toLowerCase()}`">{{ subLabel }}</span>
          </div>
          <div v-if="row.kind !== 'FUNDS' && row.name" class="t-name">{{ row.name }}</div>
        </div>
      </div>
    </td>
    <td v-if="showWalletColumn">
      <span class="wallet-ref">
        <span class="wref-dot" :style="{ background: WALLET_TYPES[row.kind].accent }" />
        {{ row.walletName }}
      </span>
    </td>
    <td class="c-num has-text-right">
      {{ row.quantity == null ? '—' : fmt.qty(row.quantity) }}
    </td>
    <td class="c-num has-text-right">
      <span v-if="priceAmount == null" class="gl-empty">—</span>
      <template v-else>{{ money(priceAmount) }}</template>
      <div v-if="averagePrice != null" class="avg-note">PM {{ money(averagePrice) }}</div>
    </td>
    <td class="c-num has-text-right">
      <div class="has-text-weight-bold">{{ money(row.costBasis) }}</div>
    </td>
    <td class="c-num has-text-right">
      <span v-if="row.currentValue == null" class="gl-empty">—</span>
      <template v-else>{{ money(row.currentValue) }}</template>
    </td>
    <td class="c-num has-text-right">
      <GainChip :value="gain?.amount ?? null" :pct="row.gainPct" :cur="gain?.currency" stacked />
    </td>
    <td class="c-act">
      <span class="chev">
        <b-icon :icon="expanded ? 'chevron-up' : 'chevron-down'" />
      </span>
    </td>
  </tr>
</template>

<style scoped>
.inv-row {
  cursor: pointer;
  transition: 0.1s;
}

.inv-row:hover {
  background: var(--surface-hover);
}

.inv-row.is-open {
  background: var(--surface-2);
}

.inv-row.is-frozen {
  --ice: var(--bulma-info-on-scheme);
  --ice-tint: color-mix(in srgb, var(--ice) 5%, transparent);

  background-image:
    linear-gradient(var(--ice), var(--ice)), linear-gradient(var(--ice-tint), var(--ice-tint));
  background-size:
    3px 100%,
    auto;
  background-repeat: no-repeat;
}

.inv-row.is-frozen :deep(.ticker-badge) {
  border: 1.5px dashed;
}

.inv-row.is-frozen :is(.name-meta, .wallet-ref, td.c-num) {
  opacity: 0.65;
}

.name-meta {
  min-width: 0;
}

.wallet-ref {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 13px;
  color: var(--text-2);
}

.wref-dot {
  width: 8px;
  height: 8px;
  border-radius: 3px;
  flex-shrink: 0;
}

.avg-note {
  font-size: 11px;
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}

.chev {
  color: var(--text-muted);
  display: grid;
  place-items: center;
}
</style>
