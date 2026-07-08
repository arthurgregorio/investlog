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
    auth.session = { name: 'Nova Usuária', email: 'nova@example.com', role: 'USER', status: 'PENDING' }
    router.push('/pending-approval')
    await router.isReady()

    const wrapper = mount(PendingApprovalView, { global: { plugins: [router, Buefy] } })

    expect(wrapper.text()).toContain('Nova Usuária')
    expect(wrapper.text()).toContain('aguardando aprovação')
  })

  it('shows a distinct declined message when the session was rejected', async () => {
    const auth = useAuthStore()
    auth.session = { name: 'Usuária Rejeitada', email: 'rejeitada@example.com', role: 'USER', status: 'REJECTED' }
    router.push('/pending-approval')
    await router.isReady()

    const wrapper = mount(PendingApprovalView, { global: { plugins: [router, Buefy] } })

    expect(wrapper.text()).toContain('Usuária Rejeitada')
    expect(wrapper.text()).toContain('não foi aprovado')
    expect(wrapper.text()).not.toContain('aguardando aprovação')
  })

  it('redirects to login after logging out', async () => {
    const auth = useAuthStore()
    auth.session = { name: 'Nova Usuária', email: 'nova@example.com', role: 'USER', status: 'PENDING' }
    const logoutSpy = vi.spyOn(auth, 'logout').mockResolvedValue()
    router.push('/pending-approval')
    await router.isReady()

    const wrapper = mount(PendingApprovalView, { global: { plugins: [router, Buefy] } })

    await wrapper.get('button').trigger('click')
    await flushPromises()

    expect(logoutSpy).toHaveBeenCalled()
    expect(router.currentRoute.value.name).toBe('login')
  })
})

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}
