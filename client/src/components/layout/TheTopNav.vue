<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useOverviewStore } from '@/stores/overview'
import { fmt } from '@/composables/useFormat'
import { useAuthStore } from '@/stores/auth'

interface NavItem {
  name?: string
  label: string
  icon: string
  children?: NavItem[]
}

const overviewStore = useOverviewStore()
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

onMounted(() => {
  overviewStore.load()
})

const nav = computed<NavItem[]>(() => {
  const items: NavItem[] = [
    { name: 'overview', label: 'Visão geral', icon: 'view-dashboard-outline' },
    { name: 'wallets', label: 'Carteiras', icon: 'wallet-outline' },
    { name: 'investments', label: 'Investimentos', icon: 'layers-outline' },
  ]
  if (auth.isAdmin) {
    items.push({
      label: 'Configurações',
      icon: 'cog-outline',
      children: [
        { name: 'settings-price-currencies', label: 'Preços e Moedas', icon: 'currency-usd' },
        { name: 'settings-users', label: 'Usuários', icon: 'account-group-outline' },
        { name: 'settings-types', label: 'Tipos', icon: 'shape-outline' },
      ],
    })
  }
  return items
})

function isActive(item: NavItem): boolean {
  if (item.name === route.name) return true
  return item.children?.some((child) => child.name === route.name) ?? false
}
</script>

<template>
  <nav class="topnav">
    <div class="topnav-inner">
      <div class="topnav-items">
        <template v-for="item in nav" :key="item.label">
          <b-dropdown v-if="item.children" aria-role="menu" append-to-body>
            <template #trigger>
              <button
                type="button"
                class="nav-item"
                :class="{ active: isActive(item) }"
                :title="item.label"
              >
                <span class="nav-icon"><b-icon :icon="item.icon" size="is-small" /></span>
                <span class="nav-label">{{ item.label }}</span>
              </button>
            </template>
            <b-dropdown-item
              v-for="child in item.children"
              :key="child.name"
              aria-role="menuitem"
              @click="router.push({ name: child.name })"
            >
              <b-icon :icon="child.icon" size="is-small" /> {{ child.label }}
            </b-dropdown-item>
          </b-dropdown>
          <button
            v-else
            class="nav-item"
            :class="{ active: isActive(item) }"
            :title="item.label"
            @click="router.push({ name: item.name })"
          >
            <span class="nav-icon"><b-icon :icon="item.icon" size="is-small" /></span>
            <span class="nav-label">{{ item.label }}</span>
          </button>
        </template>
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
