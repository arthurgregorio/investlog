<script setup lang="ts">
import { computed, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import TickerBadge from '@/components/ui/TickerBadge.vue'
import GainChip from '@/components/ui/GainChip.vue'
import PositionAdder from '@/components/investments/PositionAdder.vue'
import { usePortfolioStore } from '@/stores/portfolio'
import { fmt } from '@/composables/useFormat'
import type { Holding } from '@/types'

const props = defineProps<{ holding: Holding }>()

const store = usePortfolioStore()
const isOpen = ref(false)
const adding = ref(false)

const wallet = computed(() => store.walletOf(props.holding.walletId)!)
const cost = computed(() => store.holdingCost(props.holding))
const qty = computed(() => store.holdingQty(props.holding))
const currentValue = computed(() => store.holdingCurrentValue(props.holding))
const gain = computed(() => store.holdingGain(props.holding))

const displayName = computed(() => (props.holding.kind === 'fund' ? props.holding.name : props.holding.ticker))
const subLabel = computed(() => {
  const h = props.holding
  if (h.kind === 'fund') return h.fundType || 'Fundo'
  if (h.kind === 'crypto') return 'Cripto'
  return h.stockType
})

const fund = computed(() => (props.holding.kind === 'fund' ? props.holding : null))
const trade = computed(() => (props.holding.kind !== 'fund' ? props.holding : null))

function toggle() {
  isOpen.value = !isOpen.value
}
</script>

<template>
  <tr class="inv-row" :class="{ 'is-open': isOpen }" @click="toggle">
    <td class="c-name">
      <div class="name-cell">
        <TickerBadge :ticker="displayName" :color="store.badgeFor(holding)" />
        <div class="name-meta">
          <div class="name-line">
            <span class="t-ticker">{{ displayName }}</span>
            <span class="type-tag" :class="`tt-${wallet.type}`">{{ subLabel }}</span>
          </div>
          <div v-if="holding.kind !== 'fund' && holding.name" class="t-name">{{ holding.name }}</div>
        </div>
      </div>
    </td>
    <td>
      <span class="wallet-ref">
        <span class="wref-dot" :style="{ background: store.WALLET_TYPES[wallet.type].accent }" />{{ wallet.name }}
      </span>
    </td>
    <td class="c-num">{{ qty == null ? '—' : fmt.qty(qty) }}</td>
    <td class="c-num"><div class="cell-strong">{{ fmt.money(cost, wallet.currency) }}</div></td>
    <td class="c-num">
      <span v-if="currentValue == null" class="gl-empty">—</span>
      <template v-else>{{ fmt.money(currentValue, wallet.currency) }}</template>
    </td>
    <td class="c-num">
      <GainChip :value="gain ? gain.value : null" :pct="gain ? gain.pct : null" :cur="wallet.currency" />
    </td>
    <td class="c-act">
      <span class="chev"><AppIcon :name="isOpen ? 'chevronUp' : 'chevronDown'" :size="18" /></span>
    </td>
  </tr>

  <tr v-if="isOpen" class="detail-row">
    <td :colspan="7">
      <div class="detail">
        <table class="sub-table">
          <thead>
            <tr>
              <th>{{ fund ? 'Data do aporte' : 'Data da compra' }}</th>
              <th v-if="trade" class="c-num">Qtd.</th>
              <th v-if="trade" class="c-num">Preço</th>
              <th class="c-num">{{ fund ? 'Valor' : 'Subtotal' }}</th>
            </tr>
          </thead>
          <tbody>
            <template v-if="fund">
              <tr v-for="c in fund.contributions" :key="c.id">
                <td>{{ fmt.date(c.date) }}</td>
                <td class="c-num">{{ fmt.money(c.amount, wallet.currency) }}</td>
              </tr>
            </template>
            <template v-else-if="trade">
              <tr v-for="l in trade.lots" :key="l.id">
                <td>{{ fmt.date(l.date) }}</td>
                <td class="c-num">{{ fmt.qty(l.qty) }}</td>
                <td class="c-num">{{ fmt.money(l.price, wallet.currency) }}</td>
                <td class="c-num">{{ fmt.money(l.qty * l.price, wallet.currency) }}</td>
              </tr>
            </template>
          </tbody>
        </table>

        <PositionAdder v-if="adding" :holding="holding" @close="adding = false" />
        <div v-else class="detail-foot">
          <b-button type="is-text" icon-left="plus" @click="adding = true">
            {{ fund ? 'Registrar novo aporte' : 'Registrar nova compra' }}
          </b-button>
          <div class="detail-foot-right">
            <span v-if="trade" class="avg-note">
              Preço médio <b>{{ fmt.money(cost / (qty || 1), wallet.currency) }}</b>
            </span>
            <b-button type="is-text" size="is-small" icon-left="delete" style="color: var(--down)" @click="store.removeHolding(holding.id)">Remover</b-button>
          </div>
        </div>
      </div>
    </td>
  </tr>
</template>
