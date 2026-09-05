import { describe, expect, it, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia, type TestingPinia } from '@pinia/testing'
import { createRouter, createMemoryHistory } from 'vue-router'
import PendingApprovalView from './PendingApprovalView.vue'
import { useAuthStore } from '@/stores/auth'

describe('PendingApprovalView', () => {
  let router: ReturnType<typeof createRouter>
  let pinia: TestingPinia

  beforeEach(() => {
    pinia = createTestingPinia()
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

    const wrapper = mount(PendingApprovalView, { global: { plugins: [pinia, router] } })

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

    const wrapper = mount(PendingApprovalView, { global: { plugins: [pinia, router] } })

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
    router.push('/pending-approval')
    await router.isReady()

    const wrapper = mount(PendingApprovalView, { global: { plugins: [pinia, router] } })

    await wrapper.get('button').trigger('click')
    await flushPromises()

    expect(auth.logout).toHaveBeenCalled()
  })
})

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}
