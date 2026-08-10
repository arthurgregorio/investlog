import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import Buefy from 'buefy'
import PendingApprovalView from './PendingApprovalView.vue'
import { useAuthStore } from '@/stores/auth'

describe('PendingApprovalView', () => {
  let router: ReturnType<typeof createRouter>

  beforeEach(() => {
    setActivePinia(createPinia())
    router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/login', name: 'login', component: { template: '<div />' } },
        { path: '/pending-approval', name: 'pending-approval', component: PendingApprovalView },
      ],
    })
  })

  it('shows a generic message when there is no session', async () => {
    router.push('/pending-approval')
    await router.isReady()

    const wrapper = mount(PendingApprovalView, { global: { plugins: [router, Buefy] } })

    expect(wrapper.text()).toContain('Cadastro enviado')
  })

  it('shows a personalized message when a pending session exists', async () => {
    const auth = useAuthStore()
    auth.session = {
      name: 'Nova Usuária',
      email: 'nova@example.com',
      role: 'USER',
      status: 'PENDING',
      authProvider: 'LOCAL',
      demoModeEnabled: false,
    }
    router.push('/pending-approval')
    await router.isReady()

    const wrapper = mount(PendingApprovalView, { global: { plugins: [router, Buefy] } })

    expect(wrapper.text()).toContain('Nova Usuária')
    expect(wrapper.text()).toContain('aguardando aprovação')
  })

  it('calls auth.logout when the sign-out button is clicked', async () => {
    const auth = useAuthStore()
    auth.session = {
      name: 'Nova Usuária',
      email: 'nova@example.com',
      role: 'USER',
      status: 'PENDING',
      authProvider: 'LOCAL',
      demoModeEnabled: false,
    }
    const logoutSpy = vi.spyOn(auth, 'logout').mockResolvedValue()
    router.push('/pending-approval')
    await router.isReady()

    const wrapper = mount(PendingApprovalView, { global: { plugins: [router, Buefy] } })

    await wrapper.get('button').trigger('click')
    await flushPromises()

    expect(logoutSpy).toHaveBeenCalled()
  })
})

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}
