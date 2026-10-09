<script setup lang="ts">
import { ref } from 'vue'
import Card from '@/components/ui/Card.vue'
import WalletMovesTable from '@/components/wallets/WalletMovesTable.vue'
import { useWalletMovesStore } from '@/stores/walletMoves'

defineProps<{ walletId: string; holdingsCount: number }>()

defineSlots<{ default(): unknown }>()

const walletMovesStore = useWalletMovesStore()

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
          <span class="tag is-rounded ml-2">{{ walletMovesStore.totalElements }}</span>
        </template>
        <WalletMovesTable :wallet-id="walletId" />
      </b-tab-item>
    </b-tabs>
  </Card>
</template>

<style scoped>
.wd-tabs :deep(.tabs) {
  margin-bottom: 0;
}

.wd-tabs :deep(.tabs ul) {
  gap: 2px;
  margin-inline-start: 0;
  padding: 0 5px;
}

.wd-tabs :deep(.tabs a) {
  position: relative;
  height: 48px;
  padding: 0 15px;
  border-bottom: none;
  color: var(--text-2);
  font-size: 14px;
  font-weight: 500;
  transition: 0.13s;
}

.wd-tabs :deep(.tabs a:hover) {
  color: var(--text);
}

.wd-tabs :deep(.tabs li.is-active a) {
  color: color-mix(in srgb, var(--primary) 65%, var(--text));
  font-weight: 600;
}

.wd-tabs :deep(.tabs li.is-active a::after) {
  content: '';
  position: absolute;
  left: 13px;
  right: 13px;
  bottom: -1px;
  height: 2px;
  background: var(--primary);
  border-radius: 2px 2px 0 0;
}

.wd-tabs :deep(.tab-content) {
  padding: 0;
}
</style>
