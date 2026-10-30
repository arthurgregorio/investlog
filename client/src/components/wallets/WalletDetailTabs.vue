<script setup lang="ts">
import { ref } from 'vue'
import Card from '@/components/ui/Card.vue'
import WalletLedgerTable from '@/components/wallets/WalletLedgerTable.vue'
import { useWalletLedgerStore } from '@/stores/walletLedger'

defineProps<{ walletId: string; currency: string; holdingsCount: number }>()

defineSlots<{ default(): unknown }>()

const walletLedgerStore = useWalletLedgerStore()

const activeTab = ref(0)
</script>

<template>
  <Card class="table-card mb-0">
    <b-tabs v-model="activeTab" class="wd-tabs" :animated="false">
      <b-tab-item>
        <template #header>
          <span>Investimentos</span>
          <span class="tag is-rounded ml-2">{{ holdingsCount }}</span>
        </template>
        <slot />
      </b-tab-item>

      <b-tab-item>
        <template #header>
          <span>Movimentações</span>
          <span class="tag is-rounded ml-2">{{ walletLedgerStore.totalElements }}</span>
        </template>
        <WalletLedgerTable :wallet-id="walletId" :currency="currency" />
      </b-tab-item>
    </b-tabs>
  </Card>
</template>
