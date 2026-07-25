import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'

vi.mock('@/api/auth', () => ({
  authApi: {
    login: vi.fn(),
    logout: vi.fn(),
    fetchSession: vi.fn(),
    enroll: vi.fn(),
    verify: vi.fn(),
    register: vi.fn(),
  },
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

  it('allows navigation when an approved session exists', async () => {
    vi.resetModules()
    const auth = useAuthStore()
    auth.session = {
      name: 'Administrador',
      email: 'admin@admin.com',
      role: 'ADMIN',
      status: 'APPROVED',
      authProvider: 'LOCAL',
    }

    const { router } = await import('./index')
    router.push('/wallets')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('wallets')
  })

  it('redirects a pending session to /pending-approval', async () => {
    vi.resetModules()
    const auth = useAuthStore()
    auth.session = {
      name: 'Nova Usuária',
      email: 'nova@example.com',
      role: 'USER',
      status: 'PENDING',
      authProvider: 'LOCAL',
    }

    const { router } = await import('./index')
    router.push('/wallets')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('pending-approval')
  })

  it('redirects an approved session away from /pending-approval', async () => {
    vi.resetModules()
    const auth = useAuthStore()
    auth.session = {
      name: 'Administrador',
      email: 'admin@admin.com',
      role: 'ADMIN',
      status: 'APPROVED',
      authProvider: 'LOCAL',
    }

    const { router } = await import('./index')
    router.push('/pending-approval')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('overview')
  })

  it('redirects a non-admin away from /settings', async () => {
    vi.resetModules()
    const auth = useAuthStore()
    auth.session = {
      name: 'Usuário Comum',
      email: 'user@example.com',
      role: 'USER',
      status: 'APPROVED',
      authProvider: 'LOCAL',
    }

    const { router } = await import('./index')
    router.push('/settings')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('overview')
  })

  it('redirects a non-admin away from /users', async () => {
    vi.resetModules()
    const auth = useAuthStore()
    auth.session = {
      name: 'Usuário Comum',
      email: 'user@example.com',
      role: 'USER',
      status: 'APPROVED',
      authProvider: 'LOCAL',
    }

    const { router } = await import('./index')
    router.push('/users')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('overview')
  })
})
