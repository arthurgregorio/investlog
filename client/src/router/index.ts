import { createRouter, createWebHistory } from 'vue-router'

import OverviewView from '@/views/OverviewView.vue'
import WalletsView from '@/views/WalletsView.vue'
import InvestmentsView from '@/views/InvestmentsView.vue'
import SettingsView from '@/views/SettingsView.vue'

export const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: { name: 'overview' } },
    { path: '/overview', name: 'overview', component: OverviewView },
    { path: '/wallets', name: 'wallets', component: WalletsView },
    // ?filter=stocks|crypto|funds preselects the segmented tab
    { path: '/investments', name: 'investments', component: InvestmentsView },
    { path: '/settings', name: 'settings', component: SettingsView },
  ],
  scrollBehavior() {
    return { top: 0 }
  },
})
