<script setup lang="ts">
import Card from '@/components/ui/Card.vue'
import CardBody from '@/components/ui/CardBody.vue'
import GainChip from '@/components/ui/GainChip.vue'
import { fmt } from '@/composables/useFormat'
import type { WalletKind } from '@/types'
import type { WalletKindRow } from '@/utils/walletKindRows'

defineProps<{ row: WalletKindRow; currency: string }>()

const emit = defineEmits<{ 'goto-type': [kind: WalletKind] }>()
</script>

<template>
  <Card class="type-card" @click="emit('goto-type', row.key)">
    <CardBody>
      <div class="is-flex is-align-items-center is-gap-1 mb-3">
        <span class="type-ic" :style="{ background: row.accent }">
          <b-icon :icon="row.icon" />
        </span>
        <div class="type-name">{{ row.label }}</div>
        <div class="type-meta">
          <span>{{ row.walletCount }} {{ row.walletCount === 1 ? 'carteira' : 'carteiras' }}</span>
          <span class="dot">·</span>
          <span>{{ row.holdings }} {{ row.holdings === 1 ? 'ativo' : 'ativos' }}</span>
        </div>
      </div>
      <div class="type-value">
        {{ fmt.money(row.invested, currency, { compact: true }) }}
      </div>
      <div class="result-row">
        <div class="result-item">
          <div class="result-label">Valor atual</div>
          <div class="result-value">
            {{ fmt.money(row.currentValue, currency, { compact: true }) }}
          </div>
        </div>
        <div class="result-item">
          <div class="result-label">Resultado</div>
          <GainChip :value="row.gain" :pct="row.gainPct" :cur="currency" compact />
        </div>
      </div>
    </CardBody>
  </Card>
</template>

<style scoped>
.type-card {
  cursor: pointer;
  transition: 0.14s;
}

.type-card:hover {
  border-color: var(--text-muted);
  transform: translateY(-1px);
}

.type-name {
  font-size: 14.5px;
  font-weight: 700;
}

.type-value {
  font-size: 21px;
  font-weight: 800;
  letter-spacing: -0.02em;
}

.type-meta {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-left: auto;
  font-size: 12.5px;
  color: var(--text-muted);
}

.type-meta .dot {
  opacity: 0.5;
}

.result-row {
  margin: 11px 0;
}
</style>
