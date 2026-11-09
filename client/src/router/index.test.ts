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
    sessionStorage.clear()
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
      demoModeEnabled: false,
    }

    const { router } = await import('./index')
    router.push('/wallets')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('wallets')
  })

  it('redirects an approved session away from /login', async () => {
    vi.resetModules()
    const auth = useAuthStore()
    auth.session = {
      name: 'Administrador',
      email: 'admin@admin.com',
      role: 'ADMIN',
      status: 'APPROVED',
      authProvider: 'LOCAL',
      demoModeEnabled: false,
    }

    const { router } = await import('./index')
    router.push('/login')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('overview')
  })

  it('lets a session that is ending reach /login', async () => {
    vi.resetModules()
    const auth = useAuthStore()
    auth.session = {
      name: 'Administrador',
      email: 'admin@admin.com',
      role: 'ADMIN',
      status: 'APPROVED',
      authProvider: 'LOCAL',
      demoModeEnabled: false,
    }
    auth.endingSession = true

    const { router } = await import('./index')
    router.push('/login')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('login')
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
      demoModeEnabled: false,
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
      demoModeEnabled: false,
    }

    const { router } = await import('./index')
    router.push('/pending-approval')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('overview')
  })

  it('redirects a non-admin away from /settings/price-currencies', async () => {
    vi.resetModules()
    const auth = useAuthStore()
    auth.session = {
      name: 'Usuário Comum',
      email: 'user@example.com',
      role: 'USER',
      status: 'APPROVED',
      authProvider: 'LOCAL',
      demoModeEnabled: false,
    }

    const { router } = await import('./index')
    router.push('/settings/price-currencies')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('overview')
  })

  it('lets a non-admin reach /notifications', async () => {
    vi.resetModules()
    const auth = useAuthStore()
    auth.session = {
      name: 'Usuário Comum',
      email: 'user@example.com',
      role: 'USER',
      status: 'APPROVED',
      authProvider: 'GOOGLE',
      demoModeEnabled: false,
    }

    const { router } = await import('./index')
    router.push('/notifications')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('notifications')
  })

  it('redirects a non-admin away from /settings/users', async () => {
    vi.resetModules()
    const auth = useAuthStore()
    auth.session = {
      name: 'Usuário Comum',
      email: 'user@example.com',
      role: 'USER',
      status: 'APPROVED',
      authProvider: 'LOCAL',
      demoModeEnabled: false,
    }

    const { router } = await import('./index')
    router.push('/settings/users')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('overview')
  })

  it('redirects a non-admin away from /settings/email', async () => {
    vi.resetModules()
    const auth = useAuthStore()
    auth.session = {
      name: 'Usuário Comum',
      email: 'user@example.com',
      role: 'USER',
      status: 'APPROVED',
      authProvider: 'LOCAL',
      demoModeEnabled: false,
    }

    const { router } = await import('./index')
    router.push('/settings/email')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('overview')
  })

  it('lets an admin reach /settings/email', async () => {
    vi.resetModules()
    const auth = useAuthStore()
    auth.session = {
      name: 'Administrador',
      email: 'admin@admin.com',
      role: 'ADMIN',
      status: 'APPROVED',
      authProvider: 'LOCAL',
      demoModeEnabled: false,
    }

    const { router } = await import('./index')
    router.push('/settings/email')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('settings-email')
  })

  it('redirects /settings to /settings/price-currencies for an admin', async () => {
    vi.resetModules()
    const auth = useAuthStore()
    auth.session = {
      name: 'Administrador',
      email: 'admin@admin.com',
      role: 'ADMIN',
      status: 'APPROVED',
      authProvider: 'LOCAL',
      demoModeEnabled: false,
    }

    const { router } = await import('./index')
    router.push('/settings')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('settings-price-currencies')
  })

  describe('post-login redirect', () => {
    function approvedSession(role: 'ADMIN' | 'USER') {
      useAuthStore().session = {
        name: 'Usuário',
        email: 'user@example.com',
        role,
        status: 'APPROVED',
        authProvider: 'LOCAL',
        demoModeEnabled: false,
      }
    }

    it('remembers the deep link a visitor without a session opened', async () => {
      vi.resetModules()
      const { router } = await import('./index')
      const { consumePostLoginRedirect } = await import('./postLoginRedirect')
      router.push('/wallets/42?tab=ledger')
      await router.isReady()

      expect(router.currentRoute.value.name).toBe('login')
      expect(consumePostLoginRedirect()).toBe('/wallets/42?tab=ledger')
    })

    it('sends an approved session to the remembered path', async () => {
      vi.resetModules()
      approvedSession('ADMIN')
      const { router } = await import('./index')
      const { consumePostLoginRedirect, rememberPostLoginRedirect } =
        await import('./postLoginRedirect')
      rememberPostLoginRedirect('/wallets/42?tab=ledger')
      router.push('/overview')
      await router.isReady()

      expect(router.currentRoute.value.fullPath).toBe('/wallets/42?tab=ledger')
      expect(consumePostLoginRedirect()).toBeNull()
    })

    it('keeps the remembered path while the session is pending approval', async () => {
      vi.resetModules()
      useAuthStore().session = {
        name: 'Nova Usuária',
        email: 'nova@example.com',
        role: 'USER',
        status: 'PENDING',
        authProvider: 'LOCAL',
        demoModeEnabled: false,
      }
      const { router } = await import('./index')
      const { consumePostLoginRedirect, rememberPostLoginRedirect } =
        await import('./postLoginRedirect')
      rememberPostLoginRedirect('/wallets/42')
      router.push('/overview')
      await router.isReady()

      expect(router.currentRoute.value.name).toBe('pending-approval')
      expect(consumePostLoginRedirect()).toBe('/wallets/42')
    })

    it('still keeps a non-admin out of a remembered admin-only path', async () => {
      vi.resetModules()
      approvedSession('USER')
      const { router } = await import('./index')
      const { consumePostLoginRedirect, rememberPostLoginRedirect } =
        await import('./postLoginRedirect')
      rememberPostLoginRedirect('/settings/users')
      router.push('/overview')
      await router.isReady()

      expect(router.currentRoute.value.name).toBe('overview')
      expect(consumePostLoginRedirect()).toBeNull()
    })
  })
})
