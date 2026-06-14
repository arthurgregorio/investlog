<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { useRoute, useRouter } from 'vue-router'
import AppIcon, { type IconName } from '@/components/AppIcon.vue'
import { usePortfolioStore } from '@/stores/portfolio'
import { fmt } from '@/composables/useFormat'

const portfolio = usePortfolioStore()
const { base, grandInvestedBase } = storeToRefs(portfolio)
const route = useRoute()
const router = useRouter()

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
        <span class="tn-value">{{ fmt.money(grandInvestedBase, base, { compact: true }) }}</span>
        <span class="tn-sub">· {{ base }}</span>
      </div>
    </div>
  </nav>
</template>
