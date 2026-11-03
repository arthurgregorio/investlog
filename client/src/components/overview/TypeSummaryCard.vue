<script setup lang="ts">
import { computed } from 'vue'
import Card from '@/components/ui/Card.vue'
import CardBody from '@/components/ui/CardBody.vue'
import GainChip from '@/components/ui/GainChip.vue'
import { fmt } from '@/composables/useFormat'
import type { WalletKind } from '@/types'
import type { WalletKindRow } from '@/utils/walletKindRows'

const props = defineProps<{ row: WalletKindRow; currency: string }>()

const emit = defineEmits<{ 'goto-type': [kind: WalletKind]; 'create-wallet': [kind: WalletKind] }>()

const hasWallet = computed(() => props.row.walletCount > 0)
</script>

<template>
  <Card class="summary-card" data-testid="type-summary-card">
    <CardBody>
      <div class="is-flex is-align-items-center is-gap-1">
        <span class="type-ic" :style="{ background: row.accent }">
          <b-icon :icon="row.icon" />
        </span>
        <div>
          <div class="type-name">{{ row.label }}</div>
          <div class="is-size-7 has-text-grey" data-testid="type-meta">
            {{ row.walletCount }} {{ row.walletCount === 1 ? 'carteira' : 'carteiras' }} ·
            {{ row.holdings }} {{ row.holdings === 1 ? 'ativo' : 'ativos' }}
          </div>
        </div>
        <div class="type-value ml-auto" :class="{ 'has-text-grey': !hasWallet }">
          {{ hasWallet ? fmt.money(row.currentValue, currency, { compact: true }) : '—' }}
        </div>
      </div>
      <div class="mt-auto pt-3">
        <div v-if="hasWallet" class="result-row is-align-items-flex-end">
          <div class="result-item">
            <div class="result-label">Resultado</div>
            <GainChip :value="row.gain" :pct="row.gainPct" :cur="currency" compact />
          </div>
          <b-button
            type="is-ghost"
            size="is-small"
            class="overview-link"
            icon-right="chevron-right"
            data-testid="type-view"
            @click="emit('goto-type', row.key)"
            >Ver</b-button
          >
        </div>
        <div v-else class="result-row is-align-items-flex-end">
          <span class="is-size-7 has-text-grey">
            Nenhuma carteira de {{ row.label.toLowerCase() }}
          </span>
          <b-button
            type="is-ghost"
            size="is-small"
            class="overview-link"
            icon-right="chevron-right"
            data-testid="type-create"
            @click="emit('create-wallet', row.key)"
            >Criar</b-button
          >
        </div>
      </div>
    </CardBody>
  </Card>
</template>
