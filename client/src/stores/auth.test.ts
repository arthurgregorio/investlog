import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from './auth'
import { authApi } from '@/api/auth'
import { router } from '@/router'
import { resetUserStores } from '@/stores/resetUserStores'

vi.mock('@/api/auth', () => ({
  authApi: {
    login: vi.fn(),
    logout: vi.fn(),
    fetchSession: vi.fn(),
    enroll: vi.fn(),
    verify: vi.fn(),
    register: vi.fn(),
    linkGoogleAccount: vi.fn(),
  },
}))

vi.mock('@/router', () => ({
  router: { replace: vi.fn() },
}))

vi.mock('@/stores/resetUserStores', () => ({
  resetUserStores: vi.fn(),
}))

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('sets the session after a successful login', async () => {
    vi.mocked(authApi.login).mockResolvedValue({
      status: 'authenticated',
      session: {
        name: 'Administrador',
        email: 'admin@admin.com',
        role: 'ADMIN',
        status: 'APPROVED',
        authProvider: 'LOCAL',
        demoModeEnabled: false,
      },
    })

    const store = useAuthStore()
    const status = await store.login('admin@admin.com', 'admin')

    expect(status).toBe('authenticated')
    expect(store.session).toEqual({
      name: 'Administrador',
      email: 'admin@admin.com',
      role: 'ADMIN',
      status: 'APPROVED',
      authProvider: 'LOCAL',
      demoModeEnabled: false,
    })
    expect(router.replace).toHaveBeenCalledWith({ name: 'overview' })
  })

  it('does not set a session or navigate when enrollment is required', async () => {
    vi.mocked(authApi.login).mockResolvedValue({ status: 'needs_enrollment' })

    const store = useAuthStore()
    const status = await store.login('admin@admin.com', 'admin')

    expect(status).toBe('needs_enrollment')
    expect(store.session).toBeNull()
    expect(router.replace).not.toHaveBeenCalled()
  })

  it('navigates to the login page before clearing the session on logout', async () => {
    vi.mocked(authApi.login).mockResolvedValue({
      status: 'authenticated',
      session: {
        name: 'Administrador',
        email: 'admin@admin.com',
        role: 'ADMIN',
        status: 'APPROVED',
        authProvider: 'LOCAL',
        demoModeEnabled: false,
      },
    })
    vi.mocked(authApi.logout).mockResolvedValue(undefined)
    const store = useAuthStore()
    await store.login('admin@admin.com', 'admin')
    const stateAtNavigation: { email?: string; endingSession?: boolean; storeResets?: number } = {}
    vi.mocked(router.replace).mockImplementationOnce(async () => {
      stateAtNavigation.email = store.session?.email
      stateAtNavigation.endingSession = store.endingSession
      stateAtNavigation.storeResets = vi.mocked(resetUserStores).mock.calls.length
    })

    await store.logout()

    expect(router.replace).toHaveBeenLastCalledWith({ name: 'login' })
    expect(stateAtNavigation).toEqual({
      email: 'admin@admin.com',
      endingSession: true,
      storeResets: 0,
    })
    expect(store.session).toBeNull()
    expect(store.endingSession).toBe(false)
    expect(resetUserStores).toHaveBeenCalledOnce()
  })

  it('still clears the session and the user stores when the logout navigation fails', async () => {
    vi.mocked(authApi.logout).mockResolvedValue(undefined)
    vi.mocked(router.replace).mockRejectedValueOnce(new Error('navigation aborted'))
    const store = useAuthStore()
    store.session = {
      name: 'Administrador',
      email: 'admin@admin.com',
      role: 'ADMIN',
      status: 'APPROVED',
      authProvider: 'LOCAL',
      demoModeEnabled: false,
    }

    await expect(store.logout()).rejects.toThrow('navigation aborted')

    expect(store.session).toBeNull()
    expect(store.endingSession).toBe(false)
    expect(resetUserStores).toHaveBeenCalledOnce()
  })

  it('restoreSession leaves the session null when unauthenticated', async () => {
    vi.mocked(authApi.fetchSession).mockRejectedValue({ response: { status: 401 } })

    const store = useAuthStore()
    await store.restoreSession()

    expect(store.session).toBeNull()
  })

  it('restoreSession populates the session when authenticated', async () => {
    vi.mocked(authApi.fetchSession).mockResolvedValue({
      name: 'Administrador',
      email: 'admin@admin.com',
      role: 'ADMIN',
      status: 'APPROVED',
      authProvider: 'LOCAL',
      demoModeEnabled: false,
    })

    const store = useAuthStore()
    await store.restoreSession()

    expect(store.session?.email).toBe('admin@admin.com')
  })

  it('enrollTotp delegates to the API and returns the enrollment payload', async () => {
    vi.mocked(authApi.enroll).mockResolvedValue({
      secretKey: 'JBSWY3DPEHPK3PXP',
      qrCodeDataUri: 'data:image/png;base64,abc',
    })

    const store = useAuthStore()
    const enrollment = await store.enrollTotp('admin@admin.com', 'admin')

    expect(authApi.enroll).toHaveBeenCalledWith('admin@admin.com', 'admin')
    expect(enrollment.secretKey).toBe('JBSWY3DPEHPK3PXP')
  })

  it('verifyTotp sets the session on success', async () => {
    vi.mocked(authApi.verify).mockResolvedValue({
      name: 'Administrador',
      email: 'admin@admin.com',
      role: 'ADMIN',
      status: 'APPROVED',
      authProvider: 'LOCAL',
      demoModeEnabled: false,
    })

    const store = useAuthStore()
    await store.verifyTotp('admin@admin.com', 'admin', '123456')

    expect(authApi.verify).toHaveBeenCalledWith('admin@admin.com', 'admin', '123456')
    expect(store.session?.email).toBe('admin@admin.com')
    expect(router.replace).toHaveBeenCalledWith({ name: 'overview' })
  })

  it('linkGoogleAccount sets the session and navigates to the overview', async () => {
    vi.mocked(authApi.linkGoogleAccount).mockResolvedValue({
      name: 'Administrador',
      email: 'admin@admin.com',
      role: 'ADMIN',
      status: 'APPROVED',
      authProvider: 'LOCAL',
      demoModeEnabled: false,
    })

    const store = useAuthStore()
    await store.linkGoogleAccount('link-token', 'admin')

    expect(authApi.linkGoogleAccount).toHaveBeenCalledWith('link-token', 'admin')
    expect(store.session?.email).toBe('admin@admin.com')
    expect(router.replace).toHaveBeenCalledWith({ name: 'overview' })
  })

  it('register delegates to the API', async () => {
    vi.mocked(authApi.register).mockResolvedValue(undefined)

    const store = useAuthStore()
    await store.register('Nova Usuária', 'nova@example.com', 'senha123')

    expect(authApi.register).toHaveBeenCalledWith('Nova Usuária', 'nova@example.com', 'senha123')
  })

  it('isAdmin reflects the session role', async () => {
    vi.mocked(authApi.login).mockResolvedValue({
      status: 'authenticated',
      session: {
        name: 'Administrador',
        email: 'admin@admin.com',
        role: 'ADMIN',
        status: 'APPROVED',
        authProvider: 'LOCAL',
        demoModeEnabled: false,
      },
    })

    const store = useAuthStore()
    expect(store.isAdmin).toBe(false)
    await store.login('admin@admin.com', 'admin')
    expect(store.isAdmin).toBe(true)
  })
})
