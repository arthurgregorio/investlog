<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useOverviewStore } from '@/stores/overview'
import { fmt } from '@/composables/useFormat'
import { useAuthStore } from '@/stores/auth'

const overviewStore = useOverviewStore()
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

onMounted(() => {
  overviewStore.load()
})

const nav = computed(() => {
  const items = [
    { name: 'overview', label: 'Visão geral', icon: 'view-dashboard-outline' },
    { name: 'wallets', label: 'Carteiras', icon: 'wallet-outline' },
    { name: 'investments', label: 'Investimentos', icon: 'layers-outline' },
  ]
  if (auth.isAdmin) {
    items.push({ name: 'settings', label: 'Configurações', icon: 'cog-outline' })
  }
  return items
})
</script>

<template>
  <nav class="topnav">
    <div class="topnav-inner">
      <div class="topnav-items">
        <button
          v-for="item in nav"
          :key="item.name"
          class="nav-item"
          :class="{ active: route.name === item.name }"
          :title="item.label"
          @click="router.push({ name: item.name })"
        >
          <span class="nav-icon"><b-icon :icon="item.icon" size="is-small" /></span>
          <span class="nav-label">{{ item.label }}</span>
        </button>
      </div>
      <div class="topnav-total">
        <span class="tn-label">Total investido</span>
        <span class="tn-value">
          {{
            fmt.money(
              overviewStore.summary?.totalCostBasis ?? 0,
              overviewStore.summary?.displayCurrency,
              { compact: true },
            )
          }}
        </span>
        <span class="tn-sub">· {{ overviewStore.summary?.displayCurrency ?? 'BRL' }}</span>
      </div>
    </div>
  </nav>
</template>
