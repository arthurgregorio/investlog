<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useToast } from 'buefy'
import CurrencyRateRow from '@/components/settings/CurrencyRateRow.vue'
import PriceSyncActions from '@/components/settings/PriceSyncActions.vue'
import SettingsSection from '@/components/settings/SettingsSection.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import { useRatesStore } from '@/stores/rates'
import { useConfigurationsStore } from '@/stores/configurations'
import { useAuthStore } from '@/stores/auth'

const SYNC_TOGGLES = [
  {
    key: 'stock_price_sync_enabled',
    label: 'Atualizar preços das ações brasileiras automaticamente',
  },
  { key: 'crypto_price_sync_enabled', label: 'Atualizar preços das criptomoedas automaticamente' },
  { key: 'usd_price_sync_enabled', label: 'Atualizar cotação do dólar automaticamente' },
]

const toast = useToast()
const ratesStore = useRatesStore()
const configurationsStore = useConfigurationsStore()
const auth = useAuthStore()

const demoModeEnabled = computed(() => auth.session?.demoModeEnabled === true)

onMounted(() => {
  Promise.all([ratesStore.load(), configurationsStore.load()])
})

async function setSyncEnabled(key: string, enabled: boolean) {
  await configurationsStore.updateConfiguration(key, enabled ? 'true' : 'false')
  toast.open({
    message: enabled ? 'Sincronização automática ativada.' : 'Sincronização automática desativada.',
    type: 'is-success',
  })
}

async function updateRate(currencyCode: string, rate: number) {
  await ratesStore.upsertRate(currencyCode, rate, false)
  toast.open({ message: 'Taxa de conversão atualizada.', type: 'is-success' })
}
</script>

<template>
  <div class="page page-narrow">
    <PageHeader
      title="Preços e Moedas"
      description="Defina as taxas de conversão e a sincronização automática de preços."
    />

    <SettingsSection
      title="Moeda base e conversão"
      :description="`A visão consolidada converte cada carteira para ${ratesStore.baseCurrency} usando estas taxas.`"
      :loading="ratesStore.loading"
    >
      <template #aside>
        <span class="base-chip">
          <b-icon icon="repeat" size="is-small" />Base <b>{{ ratesStore.baseCurrency }}</b>
        </span>
      </template>
      <CurrencyRateRow
        v-for="rate in ratesStore.rates"
        :key="rate.currencyCode"
        :rate="rate"
        :base-currency="ratesStore.baseCurrency"
        @commit="updateRate(rate.currencyCode, $event)"
      />
    </SettingsSection>

    <SettingsSection
      title="Sincronização automática"
      description="Ative ou desative funções do sistema."
      :loading="configurationsStore.loading"
    >
      <b-notification v-if="demoModeEnabled" type="is-warning" :closable="false">
        Indisponível no modo demonstração.
      </b-notification>
      <b-switch
        v-for="(toggle, index) in SYNC_TOGGLES"
        :key="toggle.key"
        :class="{ 'pb-3': index < SYNC_TOGGLES.length - 1 }"
        :model-value="configurationsStore.values[toggle.key] === 'true'"
        :disabled="demoModeEnabled"
        @update:model-value="(enabled: boolean) => setSyncEnabled(toggle.key, enabled)"
      >
        {{ toggle.label }}
      </b-switch>
    </SettingsSection>

    <SettingsSection
      title="Ações administrativas"
      description="Execute ações manuais de manutenção quando necessário."
    >
      <b-notification v-if="demoModeEnabled" type="is-warning" :closable="false">
        Indisponível no modo demonstração.
      </b-notification>
      <PriceSyncActions :disabled="demoModeEnabled" />
    </SettingsSection>
  </div>
</template>
