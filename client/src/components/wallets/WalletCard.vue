<script setup lang="ts">
import EntityCard from '@/components/ui/EntityCard.vue'
import GainChip from '@/components/ui/GainChip.vue'
import { useDisplayMoney } from '@/composables/useDisplayMoney'
import { useCurrencyStore } from '@/stores/currency'
import { WALLET_TYPES } from '@/utils/walletTypes'
import type { WalletResponse } from '@/types'

defineProps<{ wallet: WalletResponse }>()

const emit = defineEmits<{ open: []; 'show-investments': [] }>()

const currencyStore = useCurrencyStore()
const { formatConverted } = useDisplayMoney()
</script>

<template>
  <EntityCard :name="wallet.name" class="mb-0">
    <template #decoration>
      <div class="wallet-stripe" :style="{ background: WALLET_TYPES[wallet.kind].accent }" />
    </template>
    <template #leading>
      <span class="type-ic sm" :style="{ background: WALLET_TYPES[wallet.kind].accent }">
        <b-icon :icon="WALLET_TYPES[wallet.kind].icon" size="is-small" />
      </span>
    </template>
    <template #tags>
      <b-tag :class="`tt-${wallet.kind.toLowerCase()}`">{{
        WALLET_TYPES[wallet.kind].label
      }}</b-tag>
      <span class="cur-chip">{{ wallet.currency }}</span>
    </template>
    <template #actions>
      <b-tooltip label="Detalhes" position="is-left">
        <b-button
          outlined
          type="is-primary"
          size="is-small"
          icon-left="finance"
          aria-label="Detalhes da carteira"
          @click.stop="emit('open')"
        />
      </b-tooltip>
    </template>

    <div class="wallet-invested">
      <div class="wallet-invested-value">
        {{ formatConverted(wallet.totalInvested, wallet.currency) }}
      </div>
      <div class="sub-caption">Investido</div>
    </div>
    <div class="result-row">
      <div class="result-item">
        <div class="result-value">
          {{
            wallet.currentValue == null
              ? '—'
              : formatConverted(wallet.currentValue, wallet.currency)
          }}
        </div>
        <div class="result-label">Valor atual</div>
      </div>
      <div class="result-item">
        <GainChip
          :value="wallet.gain == null ? null : currencyStore.convert(wallet.gain, wallet.currency)"
          :pct="wallet.gainPct"
          :cur="currencyStore.displayCurrency"
        />
        <div class="result-label">Resultado</div>
      </div>
    </div>

    <template #foot>
      <span class="wallet-count">
        {{ wallet.holdingCount }} {{ wallet.holdingCount === 1 ? 'ativo' : 'ativos' }}
      </span>
      <b-button type="is-ghost" size="is-small" @click="emit('show-investments')">
        Ver investimentos
      </b-button>
    </template>
  </EntityCard>
</template>

<style scoped>
.wallet-stripe {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  border-radius: var(--radius) var(--radius) 0 0;
}

.wallet-invested {
  margin-bottom: 14px;
}

.wallet-invested-value {
  font-size: 22px;
  font-weight: 800;
  letter-spacing: -0.02em;
}

.result-row {
  margin-bottom: 14px;
}

.result-item:last-child {
  align-items: flex-end;
  text-align: right;
}

.wallet-count {
  font-size: 12.5px;
  color: var(--text-muted);
  font-weight: 500;
}
</style>
