<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { BButton } from 'buefy'
import EmptyState from '@/components/ui/EmptyState.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import WalletAddCard from '@/components/wallets/WalletAddCard.vue'
import WalletCard from '@/components/wallets/WalletCard.vue'
import { useWalletsStore } from '@/stores/wallets'
import { useCurrencyStore } from '@/stores/currency'
import { useRatesStore } from '@/stores/rates'
import { useModals } from '@/composables/useModals'
import type { WalletResponse } from '@/types'

const walletsStore = useWalletsStore()
const currencyStore = useCurrencyStore()
const ratesStore = useRatesStore()
const router = useRouter()
const modals = useModals()

onMounted(() => {
  walletsStore.load()
  currencyStore.load()
  ratesStore.load()
})

function openWallet(wallet: WalletResponse) {
  router.push({ name: 'wallet-detail', params: { id: wallet.id } })
}

function showInvestments(wallet: WalletResponse) {
  router.push({ name: 'investments', query: { filter: wallet.kind, walletId: wallet.id } })
}
</script>

<template>
  <div class="page">
    <b-loading :is-full-page="false" :model-value="walletsStore.loading" />

    <PageHeader title="Carteiras" description="Carteiras podem ter tipos e moedas distintas" />

    <EmptyState
      v-if="walletsStore.loaded && walletsStore.wallets.length === 0"
      icon="wallet-outline"
      title="Nenhuma carteira ainda"
      text="Crie sua primeira carteira para começar a registrar investimentos."
    >
      <template #action>
        <b-button type="is-primary" icon-left="plus" @click="modals.openCreateWallet()"
          >Nova carteira</b-button
        >
      </template>
    </EmptyState>

    <div v-else class="entity-grid">
      <WalletCard
        v-for="wallet in walletsStore.wallets"
        :key="wallet.id"
        :wallet="wallet"
        @open="openWallet(wallet)"
        @show-investments="showInvestments(wallet)"
      />
      <WalletAddCard @create="modals.openCreateWallet()" />
    </div>
  </div>
</template>
