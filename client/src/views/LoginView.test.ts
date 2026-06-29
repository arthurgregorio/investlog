import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import Buefy from 'buefy'
import LoginView from './LoginView.vue'
import { useAuthStore } from '@/stores/auth'

vi.mock('@/api/auth', () => ({
  authApi: { login: vi.fn(), logout: vi.fn(), fetchSession: vi.fn() },
}))

describe('LoginView', () => {
  let router: ReturnType<typeof createRouter>

  beforeEach(() => {
    setActivePinia(createPinia())
    router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', name: 'overview', component: { template: '<div />' } },
        { path: '/login', name: 'login', component: LoginView },
      ],
    })
  })

  it('logs in and navigates to overview on submit', async () => {
    const store = useAuthStore()
    const loginSpy = vi.spyOn(store, 'login').mockResolvedValue()
    router.push('/login')
    await router.isReady()

    const wrapper = mount(LoginView, { global: { plugins: [router, Buefy] } })
    await wrapper.find('input[type="email"]').setValue('admin@admin.com')
    await wrapper.find('input[type="password"]').setValue('admin')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(loginSpy).toHaveBeenCalledWith('admin@admin.com', 'admin')
    expect(router.currentRoute.value.name).toBe('overview')
  })
})

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}
