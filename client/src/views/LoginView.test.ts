import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import Buefy from 'buefy'
import LoginView from './LoginView.vue'
import { useAuthStore } from '@/stores/auth'
import { authApi } from '@/api/auth'

vi.mock('@/api/auth', () => ({
  authApi: {
    login: vi.fn(),
    logout: vi.fn(),
    fetchSession: vi.fn(),
    enroll: vi.fn(),
    verify: vi.fn(),
    register: vi.fn(),
    fetchConfig: vi.fn().mockResolvedValue({ googleAuthEnabled: false }),
    linkGoogleAccount: vi.fn(),
  },
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
        { path: '/pending-approval', name: 'pending-approval', component: { template: '<div />' } },
      ],
    })
  })

  it('logs in on submit when already enrolled', async () => {
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
  })

  it('registers a new account and navigates to the pending-approval screen', async () => {
    const store = useAuthStore()
    const registerSpy = vi.spyOn(store, 'register').mockResolvedValue()
    router.push('/login')
    await router.isReady()

    const wrapper = mount(LoginView, { global: { plugins: [router, Buefy] } })
    await wrapper.find('[data-testid="toggle-register"]').trigger('click')
    await wrapper.find('input[type="text"]').setValue('Nova Usuária')
    await wrapper.find('input[type="email"]').setValue('nova@example.com')
    await wrapper.find('input[type="password"]').setValue('Senha123')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(registerSpy).toHaveBeenCalledWith('Nova Usuária', 'nova@example.com', 'Senha123')
    expect(router.currentRoute.value.name).toBe('pending-approval')
  })

  it('shows the server validation message when registration is rejected despite passing the client-side check', async () => {
    // Long enough, with an uppercase letter and a digit, to pass the client-side gate so the
    // (mocked) request is actually sent — the server stays the final authority even when the
    // client thinks a password is fine.
    const store = useAuthStore()
    vi.spyOn(store, 'register').mockRejectedValue({
      isAxiosError: true,
      response: {
        status: 400,
        data: { errors: ['password deve ter entre 8 e 128 caracteres'] },
      },
    })
    router.push('/login')
    await router.isReady()

    const wrapper = mount(LoginView, { global: { plugins: [router, Buefy] } })
    await wrapper.find('[data-testid="toggle-register"]').trigger('click')
    await wrapper.find('input[type="text"]').setValue('Nova Usuária')
    await wrapper.find('input[type="email"]').setValue('nova@example.com')
    await wrapper.find('input[type="password"]').setValue('SenhaValidaAgora1')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.find('.auth-error').text()).toBe('password deve ter entre 8 e 128 caracteres')
  })

  it('disables the register button until the password meets every requirement, and shows the requirement hint', async () => {
    const store = useAuthStore()
    const registerSpy = vi.spyOn(store, 'register')
    router.push('/login')
    await router.isReady()

    const wrapper = mount(LoginView, { global: { plugins: [router, Buefy] } })
    await wrapper.find('[data-testid="toggle-register"]').trigger('click')
    await wrapper.find('input[type="text"]').setValue('Nova Usuária')
    await wrapper.find('input[type="email"]').setValue('nova@example.com')

    await wrapper.find('input[type="password"]').setValue('teste')
    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('Mínimo de 8 caracteres')
    expect(wrapper.text()).toContain('Ao menos uma letra maiúscula')
    expect(wrapper.text()).toContain('Ao menos um número')

    // Long enough now, but still missing an uppercase letter and a number.
    await wrapper.find('input[type="password"]').setValue('testeteste')
    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeDefined()

    // Uppercase added, still missing a number.
    await wrapper.find('input[type="password"]').setValue('Testeteste')
    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeDefined()

    // All three requirements met.
    await wrapper.find('input[type="password"]').setValue('Senha123')
    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeUndefined()

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(registerSpy).toHaveBeenCalledWith('Nova Usuária', 'nova@example.com', 'Senha123')
  })

  it('shows the Google button when the server reports googleAuthEnabled: true', async () => {
    vi.mocked(authApi.fetchConfig).mockResolvedValueOnce({ googleAuthEnabled: true })
    router.push('/login')
    await router.isReady()

    const wrapper = mount(LoginView, { global: { plugins: [router, Buefy] } })
    await flushPromises()

    const googleButton = wrapper.find('.auth-google-button')
    expect(googleButton.exists()).toBe(true)
    // Must be a real anchor causing a full browser navigation, not a button/click handler —
    // an OAuth2 authorization-code flow requires the browser to actually leave the SPA.
    expect(googleButton.element.tagName).toBe('A')
    expect(googleButton.attributes('href')).toBe('/private/oauth2/authorization/google')
  })

  it('hides the Google button when the server reports googleAuthEnabled: false', async () => {
    router.push('/login')
    await router.isReady()

    const wrapper = mount(LoginView, { global: { plugins: [router, Buefy] } })
    await flushPromises()

    expect(wrapper.find('.auth-google-button').exists()).toBe(false)
  })

  it('shows a friendly message when redirected back with ?error=email_in_use', async () => {
    router.push('/login?error=email_in_use')
    await router.isReady()

    const wrapper = mount(LoginView, { global: { plugins: [router, Buefy] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Já existe uma conta com este e-mail')
  })

  it('shows the link-account step with the colliding email when redirected with a link token', async () => {
    router.push('/login?error=email_in_use&linkToken=abc123&linkEmail=nova%40example.com')
    await router.isReady()

    const wrapper = mount(LoginView, { global: { plugins: [router, Buefy] } })
    await flushPromises()

    expect(wrapper.text()).toContain('nova@example.com')
    expect(wrapper.find('input[type="password"]').exists()).toBe(true)
    expect(wrapper.find('input[type="email"]').exists()).toBe(false)
  })

  it('submits the link token and password to link the Google account', async () => {
    const store = useAuthStore()
    const linkSpy = vi.spyOn(store, 'linkGoogleAccount').mockResolvedValue()
    router.push('/login?error=email_in_use&linkToken=abc123&linkEmail=nova%40example.com')
    await router.isReady()

    const wrapper = mount(LoginView, { global: { plugins: [router, Buefy] } })
    await flushPromises()

    await wrapper.find('input[type="password"]').setValue('senha123')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(linkSpy).toHaveBeenCalledWith('abc123', 'senha123')
  })

  it('shows an error when linking fails', async () => {
    const store = useAuthStore()
    vi.spyOn(store, 'linkGoogleAccount').mockRejectedValue(new Error('unauthorized'))
    router.push('/login?error=email_in_use&linkToken=abc123&linkEmail=nova%40example.com')
    await router.isReady()

    const wrapper = mount(LoginView, { global: { plugins: [router, Buefy] } })
    await flushPromises()

    await wrapper.find('input[type="password"]').setValue('wrong')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Senha incorreta ou link expirado')
  })
})

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}
