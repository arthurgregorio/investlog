<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon, { type IconName } from '@/components/AppIcon.vue'
import { useOverviewStore } from '@/stores/overview'
import { fmt } from '@/composables/useFormat'

const overviewStore = useOverviewStore()
const route = useRoute()
const router = useRouter()

onMounted(() => {
  overviewStore.load()
})

const nav: { name: string; label: string; icon: IconName }[] = [
  { name: 'overview', label: 'Visão geral', icon: 'dashboard' },
  { name: 'wallets', label: 'Carteiras', icon: 'wallet' },
  { name: 'investments', label: 'Investimentos', icon: 'layers' },
  { name: 'settings', label: 'Configurações', icon: 'settings' },
]
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
          <span class="nav-icon"><AppIcon :name="item.icon" :size="18" /></span>
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
