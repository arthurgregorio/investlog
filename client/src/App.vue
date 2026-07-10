<script setup lang="ts">
import { computed, provide, ref, watchEffect } from 'vue'
import { storeToRefs } from 'pinia'
import TheNavbar from '@/components/layout/TheNavbar.vue'
import TheTopNav from '@/components/layout/TheTopNav.vue'
import CreateWalletModal from '@/components/forms/CreateWalletModal.vue'
import AddInvestmentModal from '@/components/forms/AddInvestmentModal.vue'
import { useAppearanceStore } from '@/stores/appearance'
import { ModalKey } from '@/composables/useModals'
import type { WalletKind } from '@/types'

const appearance = useAppearanceStore()
const { dark, accent } = storeToRefs(appearance)
const theme = computed(() => (dark.value ? 'dark' : 'light'))

watchEffect(() => {
  document.documentElement.setAttribute('data-theme', theme.value)
  document.documentElement.setAttribute('data-accent', accent.value)
})

const addModal = ref<{ kind: WalletKind } | null>(null)
const walletModal = ref<{ type: WalletKind } | null>(null)

function openAddInvestment(kind: WalletKind = 'STOCKS') {
  addModal.value = { kind }
}
function openCreateWallet(type: WalletKind = 'STOCKS') {
  walletModal.value = { type }
}
provide(ModalKey, { openAddInvestment, openCreateWallet })
</script>

<template>
  <div class="app-root" :data-theme="theme" :data-accent="accent" data-density="comfortable">
    <div class="main">
      <TheNavbar />
      <TheTopNav />
      <div class="content">
        <RouterView />
      </div>
    </div>

    <CreateWalletModal
      v-if="walletModal"
      :initial-type="walletModal.type"
      @close="walletModal = null"
    />
    <AddInvestmentModal
      v-if="addModal"
      :initial-kind="addModal.kind"
      @close="addModal = null"
      @create-wallet="
        (type: WalletKind) => {
          addModal = null
          openCreateWallet(type)
        }
      "
    />
  </div>
</template>
