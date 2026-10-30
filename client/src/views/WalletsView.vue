<script setup lang="ts">
import { computed, onMounted } from 'vue'
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
import { fmt } from '@/composables/useFormat'
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

const unreconciledWallets = computed(() =>
  walletsStore.wallets.filter((wallet) => !wallet.cashReconciled && wallet.cash > 0),
)
const unreconciledCash = computed(() =>
  unreconciledWallets.value.reduce(
    (total, wallet) => total + currencyStore.convert(wallet.cash, wallet.currency),
    0,
  ),
)

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

    <b-message
      v-if="unreconciledWallets.length > 0"
      type="is-warning"
      has-icon
      class="mb-0"
      data-testid="cash-reconciliation-banner"
    >
      <div
        class="is-flex is-align-items-center is-justify-content-space-between is-gap-3 is-flex-wrap-wrap"
      >
        <div>
          <strong>
            {{ unreconciledWallets.length }}
            {{ unreconciledWallets.length === 1 ? 'carteira tem' : 'carteiras têm' }}
            caixa de resgates antigos ·
            {{ fmt.money(unreconciledCash, currencyStore.displayCurrency) }}
          </strong>
          <br />
          Antes do caixa existir, o InvestLog não sabia para onde ia o dinheiro de cada resgate. Até
          você conciliar, esse valor conta como caixa nestas carteiras e como dinheiro novo onde foi
          usado.
        </div>
        <b-button
          type="is-warning"
          icon-left="scale-balance"
          data-testid="cash-reconciliation-open"
          @click="router.push({ name: 'cash-reconciliation' })"
        >
          Conciliar caixa
        </b-button>
      </div>
    </b-message>

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
