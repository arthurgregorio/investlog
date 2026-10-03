import { afterEach, describe, expect, it, vi } from 'vitest'

describe('APP_VERSION', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
    vi.resetModules()
  })

  it('defaults to dev when VITE_APP_VERSION is not set', async () => {
    vi.stubEnv('VITE_APP_VERSION', undefined)
    vi.resetModules()

    const { APP_VERSION } = await import('./appVersion')

    expect(APP_VERSION).toBe('dev')
  })

  it('exposes the version injected through VITE_APP_VERSION', async () => {
    vi.stubEnv('VITE_APP_VERSION', '1.2.3')
    vi.resetModules()

    const { APP_VERSION } = await import('./appVersion')

    expect(APP_VERSION).toBe('1.2.3')
  })
})
