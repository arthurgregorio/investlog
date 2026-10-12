import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, nextTick, type EffectScope } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { SESSION_IDLE_TIMEOUT_MINUTES, useInactivityLogout } from './useInactivityLogout'
import { recordServerActivity } from '@/api/serverActivity'
import { useAuthStore } from '@/stores/auth'
import type { SessionResponse } from '@/types'

vi.mock('@/router', () => ({ router: { replace: vi.fn() } }))
vi.mock('@/api/auth', () => ({ authApi: { logout: vi.fn() } }))

const MINUTE = 60 * 1000

const approvedSession: SessionResponse = {
  name: 'Administrador',
  email: 'admin@admin.com',
  role: 'ADMIN',
  status: 'APPROVED',
  authProvider: 'LOCAL',
  demoModeEnabled: false,
}

let visibilityState: DocumentVisibilityState = 'visible'
let scope: EffectScope | undefined

function startTracking() {
  scope = effectScope()
  scope.run(() => useInactivityLogout())
}

function switchVisibility(state: DocumentVisibilityState) {
  visibilityState = state
  document.dispatchEvent(new Event('visibilitychange'))
}

describe('useInactivityLogout', () => {
  beforeEach(() => {
    vi.useFakeTimers({ now: new Date('2026-10-12T10:00:00Z') })
    setActivePinia(createPinia())
    visibilityState = 'visible'
    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      get: () => visibilityState,
    })
    recordServerActivity()
  })

  afterEach(() => {
    scope?.stop()
    scope = undefined
    vi.useRealTimers()
  })

  function signedInAuth() {
    const auth = useAuthStore()
    auth.session = approvedSession
    vi.spyOn(auth, 'expireSession').mockResolvedValue()
    return auth
  }

  it('matches the server default of a 30-minute idle session', () => {
    expect(SESSION_IDLE_TIMEOUT_MINUTES).toBe(30)
  })

  it('does not log out before 30 minutes without an API response', () => {
    const auth = signedInAuth()
    startTracking()

    vi.advanceTimersByTime(29 * MINUTE)

    expect(auth.expireSession).not.toHaveBeenCalled()
  })

  it('logs out once 30 minutes pass without an API response', () => {
    const auth = signedInAuth()
    startTracking()

    vi.advanceTimersByTime(30 * MINUTE)

    expect(auth.expireSession).toHaveBeenCalledWith('inactivity')
  })

  it('restarts the clock on every API response', () => {
    const auth = signedInAuth()
    startTracking()

    vi.advanceTimersByTime(20 * MINUTE)
    recordServerActivity()
    vi.advanceTimersByTime(20 * MINUTE)
    expect(auth.expireSession).not.toHaveBeenCalled()

    vi.advanceTimersByTime(10 * MINUTE)
    expect(auth.expireSession).toHaveBeenCalledWith('inactivity')
  })

  it('checks immediately when the tab becomes visible again', () => {
    const auth = signedInAuth()
    startTracking()

    switchVisibility('hidden')
    vi.setSystemTime(Date.now() + 45 * MINUTE)
    expect(auth.expireSession).not.toHaveBeenCalled()

    switchVisibility('visible')

    expect(auth.expireSession).toHaveBeenCalledWith('inactivity')
  })

  it('does not run without a session', () => {
    const auth = useAuthStore()
    vi.spyOn(auth, 'expireSession').mockResolvedValue()
    startTracking()

    vi.advanceTimersByTime(60 * MINUTE)
    switchVisibility('visible')

    expect(auth.expireSession).not.toHaveBeenCalled()
  })

  it('stops once the session ends', async () => {
    const auth = signedInAuth()
    startTracking()

    auth.session = null
    await nextTick()
    vi.advanceTimersByTime(60 * MINUTE)
    switchVisibility('visible')

    expect(auth.expireSession).not.toHaveBeenCalled()
  })

  it('starts when a session begins after it was mounted', async () => {
    const auth = useAuthStore()
    vi.spyOn(auth, 'expireSession').mockResolvedValue()
    startTracking()

    auth.session = approvedSession
    await nextTick()
    vi.advanceTimersByTime(30 * MINUTE)

    expect(auth.expireSession).toHaveBeenCalledWith('inactivity')
  })
})
