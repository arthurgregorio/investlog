<script setup lang="ts">
import { reactive } from 'vue'
import { useToast } from 'buefy'
import { stockPriceSyncApi } from '@/api/stockPriceSync'
import { cryptoPriceSyncApi } from '@/api/cryptoPriceSync'

defineProps<{ disabled: boolean }>()

interface PriceSync {
  key: string
  subject: string
  successMessage: string
  run: () => Promise<unknown>
}

const PRICE_SYNCS: PriceSync[] = [
  {
    key: 'stock',
    subject: 'das ações agora',
    successMessage: 'Preços de ações atualizados.',
    run: () => stockPriceSyncApi.forceSync(),
  },
  {
    key: 'crypto',
    subject: 'das criptomoedas agora',
    successMessage: 'Preços de criptomoedas atualizados.',
    run: () => cryptoPriceSyncApi.forceSync(),
  },
]

const toast = useToast()
const triggering = reactive<Record<string, boolean>>({})

async function forceSync(sync: PriceSync) {
  triggering[sync.key] = true
  try {
    await sync.run()
    toast.open({ message: sync.successMessage, type: 'is-success' })
  } finally {
    triggering[sync.key] = false
  }
}
</script>

<template>
  <ol class="set-action-list">
    <li v-for="sync in PRICE_SYNCS" :key="sync.key" class="set-action-item">
      <span class="set-action-sentence">
        Clique para
        <b-button :loading="triggering[sync.key]" :disabled="disabled" @click="forceSync(sync)">
          atualizar as cotações
        </b-button>
        {{ sync.subject }}
      </span>
    </li>
  </ol>
</template>

<style scoped>
.set-action-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 0;
}

.set-action-item {
  list-style-position: inside;
}

.set-action-item::marker {
  color: var(--text-2);
  font-size: 13px;
}

.set-action-sentence {
  display: inline-flex;
  vertical-align: middle;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-2);
}
</style>
