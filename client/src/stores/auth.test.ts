import { describe, expect, it, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from './auth'
import { authApi } from '@/api/auth'

vi.mock('@/api/auth', () => ({
  authApi: {
    login: vi.fn(),
    logout: vi.fn(),
    fetchSession: vi.fn(),
  },
}))

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('sets the session after a successful login', async () => {
    vi.mocked(authApi.login).mockResolvedValue({ name: 'Administrador', email: 'admin@admin.com', role: 'ADMIN' })

    const store = useAuthStore()
    await store.login('admin@admin.com', 'admin')

    expect(store.session).toEqual({ name: 'Administrador', email: 'admin@admin.com', role: 'ADMIN' })
  })

  it('clears the session on logout', async () => {
    vi.mocked(authApi.login).mockResolvedValue({ name: 'Administrador', email: 'admin@admin.com', role: 'ADMIN' })
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
})
