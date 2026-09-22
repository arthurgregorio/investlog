import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia, type TestingPinia } from '@pinia/testing'
import { createRouter, createMemoryHistory } from 'vue-router'
import LoginView from './LoginView.vue'
import { useAuthStore } from '@/stores/auth'

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

// The step components own their own fields and are covered one by one in components/auth/.
// What is left here is what only the whole flow can show: which step follows which, and where
// useLoginFlow's errors and query-parameter entry points surface.
describe('LoginView flow', () => {
  let router: ReturnType<typeof createRouter>
  let pinia: TestingPinia

  beforeEach(() => {
    pinia = createTestingPinia({ stubActions: false })
    router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', name: 'overview', component: { template: '<div />' } },
        { path: '/login', name: 'login', component: LoginView },
        { path: '/pending-approval', name: 'pending-approval', component: { template: '<div />' } },
      ],
    })
  })

  async function openLogin(query = '') {
    router.push(`/login${query}`)
    await router.isReady()
    const wrapper = mount(LoginView, { global: { plugins: [pinia, router] } })
    await flushPromises()
    return wrapper
  }

  async function submitCredentials(wrapper: Awaited<ReturnType<typeof openLogin>>) {
    await wrapper.find('input[type="email"]').setValue('admin@admin.com')
    await wrapper.find('input[type="password"]').setValue('admin')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()
  }

  it('logs in on submit when already enrolled', async () => {
    const store = useAuthStore()
    const loginSpy = vi.spyOn(store, 'login').mockResolvedValue('authenticated')

    const wrapper = await openLogin()
    await submitCredentials(wrapper)

    expect(loginSpy).toHaveBeenCalledWith('admin@admin.com', 'admin')
  })

  it('moves from credentials to the enrollment step, then verifies with the code', async () => {
    const store = useAuthStore()
    vi.spyOn(store, 'login').mockResolvedValue('needs_enrollment')
    const enrollSpy = vi.spyOn(store, 'enrollTotp').mockResolvedValue({
      secretKey: 'JBSWY3DPEHPK3PXP',
      qrCodeDataUri: 'data:image/png;base64,abc',
    })
    const verifySpy = vi.spyOn(store, 'verifyTotp').mockResolvedValue()

    const wrapper = await openLogin()
    await submitCredentials(wrapper)

    expect(enrollSpy).toHaveBeenCalledWith('admin@admin.com', 'admin')
    expect(wrapper.find('img.auth-totp-qr').attributes('src')).toBe('data:image/png;base64,abc')

    await wrapper.find('input[maxlength="6"]').setValue('123456')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(verifySpy).toHaveBeenCalledWith('admin@admin.com', 'admin', '123456')
  })

  it('moves from credentials to the code step, then logs in with the code', async () => {
    const store = useAuthStore()
    const loginSpy = vi
      .spyOn(store, 'login')
      .mockResolvedValueOnce('totp_required')
      .mockResolvedValueOnce('authenticated')

    const wrapper = await openLogin()
    await submitCredentials(wrapper)

    expect(wrapper.find('img.auth-totp-qr').exists()).toBe(false)
    expect(wrapper.find('input[maxlength="6"]').exists()).toBe(true)

    await wrapper.find('input[maxlength="6"]').setValue('654321')
    await wrapper.find('input[type="checkbox"]').setValue(true)
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(loginSpy).toHaveBeenLastCalledWith('admin@admin.com', 'admin', '654321', true)
  })

  it('registers a new account and navigates to the pending-approval screen', async () => {
    const store = useAuthStore()
    const registerSpy = vi.spyOn(store, 'register').mockResolvedValue()

    const wrapper = await openLogin()
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

    const wrapper = await openLogin()
    await wrapper.find('[data-testid="toggle-register"]').trigger('click')
    await wrapper.find('input[type="text"]').setValue('Nova Usuária')
    await wrapper.find('input[type="email"]').setValue('nova@example.com')
    await wrapper.find('input[type="password"]').setValue('SenhaValidaAgora1')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.find('.auth-error').text()).toBe('password deve ter entre 8 e 128 caracteres')
  })

  it('shows the error message from a failed login attempt', async () => {
    const store = useAuthStore()
    vi.spyOn(store, 'login').mockRejectedValue(new Error('bad credentials'))

    const wrapper = await openLogin()
    await submitCredentials(wrapper)

    expect(wrapper.find('.auth-error').text()).toBe('E-mail ou senha inválidos.')
  })

  it('shows a friendly message when redirected back with ?error=email_in_use', async () => {
    const wrapper = await openLogin('?error=email_in_use')

    expect(wrapper.text()).toContain('Já existe uma conta com este e-mail')
  })

  it('shows a friendly message when redirected back with ?error=oauth_failed', async () => {
    const wrapper = await openLogin('?error=oauth_failed')

    expect(wrapper.text()).toContain('Não foi possível entrar com o Google')
  })

  it('enters the link step with the colliding e-mail when redirected with a link token', async () => {
    const wrapper = await openLogin(
      '?error=email_in_use&linkToken=abc123&linkEmail=nova%40example.com',
    )

    expect(wrapper.text()).toContain('nova@example.com')
    expect(wrapper.find('input[type="password"]').exists()).toBe(true)
    expect(wrapper.find('input[type="email"]').exists()).toBe(false)
  })

  it('submits the link token and password to link the Google account', async () => {
    const store = useAuthStore()
    const linkSpy = vi.spyOn(store, 'linkGoogleAccount').mockResolvedValue()

    const wrapper = await openLogin(
      '?error=email_in_use&linkToken=abc123&linkEmail=nova%40example.com',
    )
    await wrapper.find('input[type="password"]').setValue('senha123')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(linkSpy).toHaveBeenCalledWith('abc123', 'senha123')
  })

  it('shows an error when linking fails', async () => {
    const store = useAuthStore()
    vi.spyOn(store, 'linkGoogleAccount').mockRejectedValue(new Error('unauthorized'))

    const wrapper = await openLogin(
      '?error=email_in_use&linkToken=abc123&linkEmail=nova%40example.com',
    )
    await wrapper.find('input[type="password"]').setValue('wrong')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Senha incorreta ou link expirado')
  })
})

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}
