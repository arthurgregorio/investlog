import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import Buefy from 'buefy'
import LoginView from './LoginView.vue'
import { useAuthStore } from '@/stores/auth'

vi.mock('@/api/auth', () => ({
  authApi: { login: vi.fn(), logout: vi.fn(), fetchSession: vi.fn(), enroll: vi.fn(), verify: vi.fn() },
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

  it('logs in and navigates to overview on submit when already enrolled', async () => {
    const store = useAuthStore()
    const loginSpy = vi.spyOn(store, 'login').mockResolvedValue('authenticated')
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

  it('shows the QR enrollment step when login needs enrollment, then verifies to log in', async () => {
    const store = useAuthStore()
    vi.spyOn(store, 'login').mockResolvedValue('needs_enrollment')
    const enrollSpy = vi.spyOn(store, 'enrollTotp').mockResolvedValue({
      secretKey: 'JBSWY3DPEHPK3PXP',
      qrCodeDataUri: 'data:image/png;base64,abc',
    })
    const verifySpy = vi.spyOn(store, 'verifyTotp').mockResolvedValue()
    router.push('/login')
    await router.isReady()

    const wrapper = mount(LoginView, { global: { plugins: [router, Buefy] } })
    await wrapper.find('input[type="email"]').setValue('admin@admin.com')
    await wrapper.find('input[type="password"]').setValue('admin')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(enrollSpy).toHaveBeenCalledWith('admin@admin.com', 'admin')
    const qrImage = wrapper.find('img.auth-totp-qr')
    expect(qrImage.exists()).toBe(true)
    expect(qrImage.attributes('src')).toBe('data:image/png;base64,abc')

    await wrapper.find('input[maxlength="6"]').setValue('123456')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(verifySpy).toHaveBeenCalledWith('admin@admin.com', 'admin', '123456')
    expect(router.currentRoute.value.name).toBe('overview')
  })

  it('shows the code step when login requires a totp code, then logs in with it', async () => {
    const store = useAuthStore()
    const loginSpy = vi
      .spyOn(store, 'login')
      .mockResolvedValueOnce('totp_required')
      .mockResolvedValueOnce('authenticated')
    router.push('/login')
    await router.isReady()

    const wrapper = mount(LoginView, { global: { plugins: [router, Buefy] } })
    await wrapper.find('input[type="email"]').setValue('admin@admin.com')
    await wrapper.find('input[type="password"]').setValue('admin')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.find('img.auth-totp-qr').exists()).toBe(false)
    expect(wrapper.find('input[maxlength="6"]').exists()).toBe(true)

    await wrapper.find('input[maxlength="6"]').setValue('654321')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(loginSpy).toHaveBeenLastCalledWith('admin@admin.com', 'admin', '654321')
    expect(router.currentRoute.value.name).toBe('overview')
  })
})

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}
