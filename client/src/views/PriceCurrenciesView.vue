<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useToast } from 'buefy'
import Card from '@/components/ui/Card.vue'
import CardBody from '@/components/ui/CardBody.vue'
import NumberInput from '@/components/ui/NumberInput.vue'
import { useRatesStore } from '@/stores/rates'
import { useConfigurationsStore } from '@/stores/configurations'
import { useAuthStore } from '@/stores/auth'
import { fmt } from '@/composables/useFormat'
import { stockPriceSyncApi } from '@/api/stockPriceSync'
import { cryptoPriceSyncApi } from '@/api/cryptoPriceSync'

const toast = useToast()
const ratesStore = useRatesStore()
const configurationsStore = useConfigurationsStore()
const auth = useAuthStore()

const triggeringStockSync = ref(false)
const triggeringCryptoSync = ref(false)

const demoModeEnabled = computed(() => auth.session?.demoModeEnabled === true)

onMounted(() => {
  Promise.all([ratesStore.load(), configurationsStore.load()])
})

const stockPriceSyncEnabled = computed({
  get: () => configurationsStore.values['stock_price_sync_enabled'] === 'true',
  set: async (enabled: boolean) => {
    await configurationsStore.updateConfiguration(
      'stock_price_sync_enabled',
      enabled ? 'true' : 'false',
    )
    toast.open({
      message: enabled
        ? 'Sincronização automática ativada.'
        : 'Sincronização automática desativada.',
      type: 'is-success',
    })
  },
})

const cryptoPriceSyncEnabled = computed({
  get: () => configurationsStore.values['crypto_price_sync_enabled'] === 'true',
  set: async (enabled: boolean) => {
    await configurationsStore.updateConfiguration(
      'crypto_price_sync_enabled',
      enabled ? 'true' : 'false',
    )
    toast.open({
      message: enabled
        ? 'Sincronização automática ativada.'
        : 'Sincronização automática desativada.',
      type: 'is-success',
    })
  },
})

const usdPriceSyncEnabled = computed({
  get: () => configurationsStore.values['usd_price_sync_enabled'] === 'true',
  set: async (enabled: boolean) => {
    await configurationsStore.updateConfiguration(
      'usd_price_sync_enabled',
      enabled ? 'true' : 'false',
    )
    toast.open({
      message: enabled
        ? 'Sincronização automática ativada.'
        : 'Sincronização automática desativada.',
      type: 'is-success',
    })
  },
})

async function forceStockPriceSync() {
  triggeringStockSync.value = true
  try {
    await stockPriceSyncApi.forceSync()
    toast.open({ message: 'Preços de ações atualizados.', type: 'is-success' })
  } finally {
    triggeringStockSync.value = false
  }
}

async function forceCryptoPriceSync() {
  triggeringCryptoSync.value = true
  try {
    await cryptoPriceSyncApi.forceSync()
    toast.open({ message: 'Preços de criptomoedas atualizados.', type: 'is-success' })
  } finally {
    triggeringCryptoSync.value = false
  }
}

const rateDrafts = reactive<Record<string, number | ''>>({})

function rateDisplayValue(currencyCode: string, storedRate: number) {
  return currencyCode in rateDrafts ? rateDrafts[currencyCode] : storedRate
}

function draftRate(currencyCode: string, value: number | '') {
  rateDrafts[currencyCode] = value
}

async function commitRate(currencyCode: string) {
  const value = rateDrafts[currencyCode]
  delete rateDrafts[currencyCode]
  if (value === undefined || value === '' || value <= 0) return
  await ratesStore.upsertRate(currencyCode, Number(value), false)
  toast.open({ message: 'Taxa de conversão atualizada.', type: 'is-success' })
}
</script>

<template>
  <div class="page page-narrow">
    <div class="page-head">
      <h1 class="page-title">Preços e Moedas</h1>
      <p class="page-desc">Defina as taxas de conversão e a sincronização automática de preços.</p>
    </div>

    <Card>
      <CardBody>
        <b-loading :is-full-page="false" :active="ratesStore.loading" />
        <div class="set-head">
          <h2 class="set-title">Moeda base e conversão</h2>
          <span class="base-chip">
            <b-icon icon="repeat" size="is-small" />Base <b>{{ ratesStore.baseCurrency }}</b>
          </span>
        </div>
        <p class="set-desc">
          A visão consolidada converte cada carteira para {{ ratesStore.baseCurrency }} usando estas
          taxas.
        </p>
        <div class="rate-list">
          <div v-for="rate in ratesStore.rates" :key="rate.currencyCode" class="rate-row">
            <div class="rate-cur">
              <span class="cur-chip lg">{{ rate.currencyCode }}</span>
              <span class="rate-sym">{{ fmt.sym(rate.currencyCode) }}</span>
            </div>
            <span v-if="rate.isBase" class="rate-base">Moeda base · 1,00</span>
            <label v-else class="rate-input">
              <span>1 {{ rate.currencyCode }} =</span>
              <NumberInput
                :model-value="rateDisplayValue(rate.currencyCode, rate.rate)"
                :prefix="fmt.sym(ratesStore.baseCurrency)"
                @update:model-value="(v) => draftRate(rate.currencyCode, v)"
                @blur="commitRate(rate.currencyCode)"
              />
            </label>
          </div>
        </div>
      </CardBody>
    </Card>

    <Card>
      <CardBody>
        <b-loading :is-full-page="false" :active="configurationsStore.loading" />
        <div class="set-head"><h2 class="set-title">Sincronização automática</h2></div>
        <p class="set-desc">Ative ou desative funções do sistema.</p>
        <b-notification v-if="demoModeEnabled" type="is-warning" :closable="false">
          Indisponível no modo demonstração.
        </b-notification>
        <b-switch v-model="stockPriceSyncEnabled" class="pb-3" :disabled="demoModeEnabled">
          Atualizar preços das ações brasileiras automaticamente
        </b-switch>
        <b-switch v-model="cryptoPriceSyncEnabled" class="pb-3" :disabled="demoModeEnabled">
          Atualizar preços das criptomoedas automaticamente
        </b-switch>
        <b-switch v-model="usdPriceSyncEnabled" :disabled="demoModeEnabled">
          Atualizar cotação do dólar automaticamente
        </b-switch>
      </CardBody>
    </Card>

    <Card>
      <CardBody>
        <div class="set-head"><h2 class="set-title">Ações administrativas</h2></div>
        <p class="set-desc">Execute ações manuais de manutenção quando necessário.</p>
        <b-notification v-if="demoModeEnabled" type="is-warning" :closable="false">
          Indisponível no modo demonstração.
        </b-notification>
        <ol class="set-action-list">
          <li class="set-action-item">
            <span class="set-action-sentence">
              Clique para
              <b-button
                :loading="triggeringStockSync"
                :disabled="demoModeEnabled"
                @click="forceStockPriceSync"
              >
                atualizar as cotações
              </b-button>
              das ações agora
            </span>
          </li>
          <li class="set-action-item">
            <span class="set-action-sentence">
              Clique para
              <b-button
                :loading="triggeringCryptoSync"
                :disabled="demoModeEnabled"
                @click="forceCryptoPriceSync"
              >
                atualizar as cotações
              </b-button>
              das criptomoedas agora
            </span>
          </li>
        </ol>
      </CardBody>
    </Card>
  </div>
</template>
