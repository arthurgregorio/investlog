<script setup lang="ts">
import { computed } from 'vue'
import FrozenBadge from '@/components/ui/FrozenBadge.vue'
import TickerBadge from '@/components/ui/TickerBadge.vue'
import { badgeColor, WALLET_TYPES } from '@/utils/walletTypes'
import type { ClassHolding } from '@/types'

const props = defineProps<{
  holding: Pick<
    ClassHolding,
    'kind' | 'walletName' | 'ticker' | 'name' | 'typeLabel' | 'segmentLabel' | 'frozen'
  >
}>()

const displayName = computed(() => props.holding.ticker ?? props.holding.name)
const secondLine = computed(() =>
  props.holding.ticker ? props.holding.name : 'Fundo de investimento',
)
const tagLabel = computed(() => props.holding.segmentLabel ?? props.holding.typeLabel ?? '—')
</script>

<template>
  <td>
    <div class="is-flex is-align-items-center is-gap-1.5">
      <FrozenBadge v-if="holding.frozen" />
      <TickerBadge v-else :ticker="displayName" :color="badgeColor(holding.ticker, holding.kind)" />
      <div class="name-meta">
        <div class="t-ticker has-text-weight-bold">{{ displayName }}</div>
        <div class="t-name">{{ secondLine }}</div>
      </div>
    </div>
  </td>
  <td>
    <div class="kind-meta">
      <span class="wallet-ref">
        <span class="wref-dot" :style="{ background: WALLET_TYPES[holding.kind].accent }" />
        {{ holding.walletName }}
      </span>
      <span class="type-tag" :class="`tt-${holding.kind.toLowerCase()}`">{{ tagLabel }}</span>
    </div>
  </td>
</template>
