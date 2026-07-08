import { describe, expect, it, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from './auth'
import { authApi } from '@/api/auth'

vi.mock('@/api/auth', () => ({
  authApi: {
    login: vi.fn(),
    logout: vi.fn(),
    fetchSession: vi.fn(),
    enroll: vi.fn(),
    verify: vi.fn(),
  },
}))

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('sets the session after a successful login', async () => {
    vi.mocked(authApi.login).mockResolvedValue({
      status: 'authenticated',
      session: { name: 'Administrador', email: 'admin@admin.com', role: 'ADMIN' },
    })

    const store = useAuthStore()
    const status = await store.login('admin@admin.com', 'admin')

    expect(status).toBe('authenticated')
    expect(store.session).toEqual({ name: 'Administrador', email: 'admin@admin.com', role: 'ADMIN' })
  })

  it('does not set a session when enrollment is required', async () => {
    vi.mocked(authApi.login).mockResolvedValue({ status: 'needs_enrollment' })

    const store = useAuthStore()
    const status = await store.login('admin@admin.com', 'admin')

    expect(status).toBe('needs_enrollment')
    expect(store.session).toBeNull()
  })

  it('clears the session on logout', async () => {
    vi.mocked(authApi.login).mockResolvedValue({
      status: 'authenticated',
      session: { name: 'Administrador', email: 'admin@admin.com', role: 'ADMIN' },
    })
    vi.mocked(authApi.logout).mockResolvedValue(undefined)

    const store = useAuthStore()
    await store.login('admin@admin.com', 'admin')
    await store.logout()

    expect(store.session).toBeNull()
  })

  it('restoreSession leaves the session null when unauthenticated', async () => {
    vi.mocked(authApi.fetchSession).mockRejectedValue({ response: { status: 401 } })

    const store = useAuthStore()
    await store.restoreSession()

    expect(store.session).toBeNull()
  })

  it('restoreSession populates the session when authenticated', async () => {
    vi.mocked(authApi.fetchSession).mockResolvedValue({ name: 'Administrador', email: 'admin@admin.com', role: 'ADMIN' })

    const store = useAuthStore()
    await store.restoreSession()

    expect(store.session?.email).toBe('admin@admin.com')
  })

  it('enrollTotp delegates to the API and returns the enrollment payload', async () => {
    vi.mocked(authApi.enroll).mockResolvedValue({ secretKey: 'JBSWY3DPEHPK3PXP', qrCodeDataUri: 'data:image/png;base64,abc' })

    const store = useAuthStore()
    const enrollment = await store.enrollTotp('admin@admin.com', 'admin')

    expect(authApi.enroll).toHaveBeenCalledWith('admin@admin.com', 'admin')
    expect(enrollment.secretKey).toBe('JBSWY3DPEHPK3PXP')
  })

  it('verifyTotp sets the session on success', async () => {
    vi.mocked(authApi.verify).mockResolvedValue({ name: 'Administrador', email: 'admin@admin.com', role: 'ADMIN' })

    const store = useAuthStore()
    await store.verifyTotp('admin@admin.com', 'admin', '123456')

    expect(authApi.verify).toHaveBeenCalledWith('admin@admin.com', 'admin', '123456')
    expect(store.session?.email).toBe('admin@admin.com')
  })
})
