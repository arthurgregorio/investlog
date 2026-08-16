import { describe, expect, it, beforeEach, afterEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import Buefy from 'buefy'
import TheTopNav from './TheTopNav.vue'
import { useAuthStore } from '@/stores/auth'
import { overviewApi } from '@/api/overview'

vi.mock('@/api/overview')

function makeRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/overview', name: 'overview', component: { template: '<div />' } },
      { path: '/wallets', name: 'wallets', component: { template: '<div />' } },
      { path: '/investments', name: 'investments', component: { template: '<div />' } },
      {
        path: '/settings/price-currencies',
        name: 'settings-price-currencies',
        component: { template: '<div />' },
      },
      { path: '/settings/types', name: 'settings-types', component: { template: '<div />' } },
      { path: '/settings/users', name: 'settings-users', component: { template: '<div />' } },
    ],
  })
}

describe('TheTopNav', () => {
  let router: ReturnType<typeof makeRouter>

  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(overviewApi.getSummary).mockResolvedValue({
      totalCostBasis: 0,
      totalCurrentValue: 0,
      totalGain: 0,
      totalGainPct: 0,
      displayCurrency: 'BRL',
      kindSummaries: [],
    })
    vi.mocked(overviewApi.getSeries).mockResolvedValue([])
    router = makeRouter()
  })

  afterEach(() => {
    // b-dropdown's append-to-body teleports outside the wrapper, so each mount leaves
    // nodes behind unless we clear the body between tests.
    document.body.innerHTML = ''
  })

  it('does not show a Configurações item for a non-admin', async () => {
    const auth = useAuthStore()
    auth.session = {
      name: 'Usuário Comum',
      email: 'user@example.com',
      role: 'USER',
      status: 'APPROVED',
      authProvider: 'LOCAL',
      demoModeEnabled: false,
    }
    router.push('/overview')
    await router.isReady()

    const wrapper = mount(TheTopNav, { global: { plugins: [router, Buefy] } })

    expect(wrapper.text()).not.toContain('Configurações')
  })

  it('shows a Configurações dropdown with three children for an admin', async () => {
    const auth = useAuthStore()
    auth.session = {
      name: 'Administrador',
      email: 'admin@admin.com',
      role: 'ADMIN',
      status: 'APPROVED',
      authProvider: 'LOCAL',
      demoModeEnabled: false,
    }
    router.push('/overview')
    await router.isReady()

    mount(TheTopNav, { global: { plugins: [router, Buefy] }, attachTo: document.body })

    // append-to-body means the dropdown panel teleports out of the component tree.
    expect(document.body.textContent).toContain('Configurações')
    expect(document.body.textContent).toContain('Preços e Moedas')
    expect(document.body.textContent).toContain('Usuários')
    expect(document.body.textContent).toContain('Tipos')
  })

  it('marks Configurações as active when on a child route', async () => {
    const auth = useAuthStore()
    auth.session = {
      name: 'Administrador',
      email: 'admin@admin.com',
      role: 'ADMIN',
      status: 'APPROVED',
      authProvider: 'LOCAL',
      demoModeEnabled: false,
    }
    router.push('/settings/types')
    await router.isReady()

    const wrapper = mount(TheTopNav, { global: { plugins: [router, Buefy] } })

    const trigger = wrapper.get('.nav-item.active')
    expect(trigger.text()).toContain('Configurações')
  })

  it('navigates to the picked child route when a dropdown item is clicked', async () => {
    const auth = useAuthStore()
    auth.session = {
      name: 'Administrador',
      email: 'admin@admin.com',
      role: 'ADMIN',
      status: 'APPROVED',
      authProvider: 'LOCAL',
      demoModeEnabled: false,
    }
    router.push('/overview')
    await router.isReady()

    mount(TheTopNav, { global: { plugins: [router, Buefy] }, attachTo: document.body })

    const usersItem = Array.from(document.body.querySelectorAll('.dropdown-item')).find((item) =>
      item.textContent?.includes('Usuários'),
    )
    usersItem?.dispatchEvent(new Event('click', { bubbles: true }))
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('settings-users')
  })
})

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}
