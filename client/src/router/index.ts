import { createRouter, createWebHistory } from 'vue-router'

import OverviewView from '@/views/OverviewView.vue'
import WalletsView from '@/views/WalletsView.vue'
import InvestmentsView from '@/views/InvestmentsView.vue'
import SettingsView from '@/views/SettingsView.vue'
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
    { path: '/settings', name: 'settings', component: SettingsView },
    { path: '/users', name: 'users', component: UsersView },
  ],
  scrollBehavior() {
    return { top: 0 }
  },
})

const PUBLIC_ROUTE_NAMES = ['login', 'pending-approval']

router.beforeEach((to) => {
  const auth = useAuthStore()

  if (!PUBLIC_ROUTE_NAMES.includes(to.name as string) && !auth.session) {
    return { name: 'login' }
  }

  if (to.name === 'login' && auth.session) {
    return auth.session.status === 'APPROVED' ? { name: 'overview' } : { name: 'pending-approval' }
  }

  if (
    auth.session &&
    auth.session.status !== 'APPROVED' &&
    !PUBLIC_ROUTE_NAMES.includes(to.name as string)
  ) {
    return { name: 'pending-approval' }
  }

  if (to.name === 'pending-approval' && auth.session?.status === 'APPROVED') {
    return { name: 'overview' }
  }

  if (
    (to.name === 'settings' || to.name === 'users') &&
    auth.session &&
    auth.session.role !== 'ADMIN'
  ) {
    return { name: 'overview' }
  }

  return true
})
