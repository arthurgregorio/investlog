import { createRouter, createWebHistory } from 'vue-router'

import OverviewView from '@/views/OverviewView.vue'
import WalletsView from '@/views/WalletsView.vue'
import InvestmentsView from '@/views/InvestmentsView.vue'
import PriceCurrenciesView from '@/views/PriceCurrenciesView.vue'
import TypesView from '@/views/TypesView.vue'
import UsersView from '@/views/UsersView.vue'
import LoginView from '@/views/LoginView.vue'
import PendingApprovalView from '@/views/PendingApprovalView.vue'
import { useAuthStore } from '@/stores/auth'

export const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: { name: 'overview' } },
    { path: '/login', name: 'login', component: LoginView },
    { path: '/pending-approval', name: 'pending-approval', component: PendingApprovalView },
    { path: '/overview', name: 'overview', component: OverviewView },
    { path: '/wallets', name: 'wallets', component: WalletsView },
    { path: '/investments', name: 'investments', component: InvestmentsView },
    { path: '/settings', redirect: { name: 'settings-price-currencies' } },
    {
      path: '/settings/price-currencies',
      name: 'settings-price-currencies',
      component: PriceCurrenciesView,
    },
    { path: '/settings/types', name: 'settings-types', component: TypesView },
    { path: '/settings/users', name: 'settings-users', component: UsersView },
  ],
  scrollBehavior() {
    return { top: 0 }
  },
})

const PUBLIC_ROUTE_NAMES = new Set(['login', 'pending-approval'])
const ADMIN_ONLY_ROUTE_NAMES = new Set([
  'settings-price-currencies',
  'settings-types',
  'settings-users',
])

router.beforeEach((to) => {
  const auth = useAuthStore()

  if (!PUBLIC_ROUTE_NAMES.has(to.name as string) && !auth.session) {
    return { name: 'login' }
  }

  if (to.name === 'login' && auth.session) {
    return auth.session.status === 'APPROVED' ? { name: 'overview' } : { name: 'pending-approval' }
  }

  if (
    auth.session &&
    auth.session.status !== 'APPROVED' &&
    !PUBLIC_ROUTE_NAMES.has(to.name as string)
  ) {
    return { name: 'pending-approval' }
  }

  if (to.name === 'pending-approval' && auth.session?.status === 'APPROVED') {
    return { name: 'overview' }
  }

  if (
    ADMIN_ONLY_ROUTE_NAMES.has(to.name as string) &&
    auth.session &&
    auth.session.role !== 'ADMIN'
  ) {
    return { name: 'overview' }
  }

  return true
})
