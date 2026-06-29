import { describe, expect, it, beforeEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'

vi.mock('@/api/auth', () => ({
  authApi: { login: vi.fn(), logout: vi.fn(), fetchSession: vi.fn() },
}))

describe('router auth guard', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('redirects to /login when there is no session', async () => {
    vi.resetModules()
    const { router } = await import('./index')
    router.push('/wallets')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('login')
  })

  it('allows navigation when a session exists', async () => {
    vi.resetModules()
    const auth = useAuthStore()
    auth.session = { name: 'Administrador', email: 'admin@admin.com', role: 'ADMIN' }

    const { router } = await import('./index')
    router.push('/wallets')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('wallets')
  })
})
