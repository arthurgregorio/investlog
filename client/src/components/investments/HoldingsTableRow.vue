<script setup lang="ts">
import { computed } from 'vue'
import FrozenBadge from '@/components/ui/FrozenBadge.vue'
import GainChip from '@/components/ui/GainChip.vue'
import TickerBadge from '@/components/ui/TickerBadge.vue'
import { useDisplayMoney } from '@/composables/useDisplayMoney'
import { fmt } from '@/composables/useFormat'
import { holdingTypeLabel } from '@/utils/holdingLabels'
import { badgeColor, WALLET_TYPES } from '@/utils/walletTypes'
import type { HoldingRow } from '@/types'

const props = defineProps<{
  row: HoldingRow
  current: boolean
  showWalletColumn: boolean
  convertToDisplayCurrency: boolean
}>()
const emit = defineEmits<{ open: []; 'add-position': []; 'update-price': [] }>()

const { displayAmount, formatDisplayAmount } = useDisplayMoney()

const isFund = computed(() => props.row.kind === 'FUNDS')
const displayName = computed(() => props.row.ticker ?? props.row.name)
const secondLine = computed(() => (props.row.ticker ? props.row.name : 'Fundo de investimento'))

const averagePrice = computed(() =>
  !isFund.value && props.row.quantity ? props.row.costBasis / props.row.quantity : null,
)

const gain = computed(() =>
  props.row.gain == null
    ? null
    : displayAmount(props.row.gain, props.row.walletCurrency, props.convertToDisplayCurrency),
)

const addLabel = computed(() => (isFund.value ? 'Registrar novo aporte' : 'Registrar nova compra'))
const updateLabel = computed(() => (isFund.value ? 'Atualizar valor atual' : 'Atualizar preço'))

function money(amount: number): string {
  return formatDisplayAmount(amount, props.row.walletCurrency, props.convertToDisplayCurrency)
}
</script>

<template>
  <tr
    class="inv-row"
    :class="{ 'is-current': current, 'is-frozen': row.frozen }"
    @click="emit('open')"
  >
    <td>
      <div class="is-flex is-align-items-center is-gap-1.5">
        <FrozenBadge v-if="row.frozen" />
        <TickerBadge v-else :ticker="displayName" :color="badgeColor(row.ticker, row.kind)" />
        <div class="name-meta">
          <button
            type="button"
            class="t-ticker open-holding"
            aria-haspopup="dialog"
            :data-open-holding="row.id"
            @click.stop="emit('open')"
          >
            {{ displayName }}
          </button>
          <div class="t-name">{{ secondLine }}</div>
        </div>
      </div>
    </td>
    <td>
      <div class="kind-meta">
        <span v-if="showWalletColumn" class="wallet-ref">
          <span class="wref-dot" :style="{ background: WALLET_TYPES[row.kind].accent }" />
          {{ row.walletName }}
        </span>
        <span class="type-tag" :class="`tt-${row.kind.toLowerCase()}`">{{
          holdingTypeLabel(row)
        }}</span>
      </div>
    </td>
    <td class="c-num has-text-right">
      {{ isFund || row.quantity == null ? '—' : fmt.qty(row.quantity) }}
    </td>
    <td class="c-num has-text-right has-text-weight-semibold">
      <span v-if="averagePrice == null" class="gl-empty">—</span>
      <template v-else>{{ money(averagePrice) }}</template>
    </td>
    <td class="c-num has-text-right">
      <span v-if="isFund || row.currentPrice == null" class="gl-empty">—</span>
      <template v-else>{{ money(row.currentPrice) }}</template>
    </td>
    <td class="c-num has-text-right has-text-weight-bold">
      <span v-if="row.currentValue == null" class="gl-empty">—</span>
      <template v-else>{{ money(row.currentValue) }}</template>
    </td>
    <td class="c-num has-text-right">
      <GainChip :value="gain?.amount ?? null" :pct="row.gainPct" :cur="gain?.currency" stacked />
    </td>
    <td class="c-act is-wide">
      <div class="is-flex is-justify-content-flex-end">
        <b-tooltip :label="addLabel" position="is-left">
          <b-button
            type="is-ghost"
            icon-left="plus"
            :aria-label="`${addLabel} — ${displayName}`"
            :disabled="row.frozen"
            data-testid="row-add-position"
            @click.stop="emit('add-position')"
          />
        </b-tooltip>
        <b-tooltip :label="updateLabel" position="is-left">
          <b-button
            type="is-ghost"
            icon-left="currency-usd"
            :aria-label="`${updateLabel} — ${displayName}`"
            data-testid="row-update-price"
            @click.stop="emit('update-price')"
          />
        </b-tooltip>
      </div>
    </td>
  </tr>
</template>

<style scoped>
.inv-row {
  cursor: pointer;
}
</style>
