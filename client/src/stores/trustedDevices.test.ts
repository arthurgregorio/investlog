import { describe, expect, it, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useTrustedDevicesStore } from './trustedDevices'
import { authApi } from '@/api/auth'

vi.mock('@/api/auth', () => ({
  authApi: {
    fetchTrustedDevices: vi.fn(),
    revokeTrustedDevice: vi.fn(),
  },
}))

describe('trustedDevices store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loads devices from the API', async () => {
    vi.mocked(authApi.fetchTrustedDevices).mockResolvedValue([
      {
        id: '1',
        label: 'Chrome em Windows',
        lastUsedAt: '2026-08-01T10:00:00Z',
        expiresAt: '2026-08-31T10:00:00Z',
      },
    ])

    const store = useTrustedDevicesStore()
    await store.load()

    expect(store.devices).toHaveLength(1)
    expect(store.devices[0].label).toBe('Chrome em Windows')
  })

  it('sets loading to true while fetching and false once resolved', async () => {
    let resolveFetch: (devices: never[]) => void
    const fetchPromise = new Promise<never[]>((resolve) => {
      resolveFetch = resolve
    })
    vi.mocked(authApi.fetchTrustedDevices).mockReturnValue(fetchPromise)

    const store = useTrustedDevicesStore()
    const loadPromise = store.load()

    expect(store.loading).toBe(true)

    resolveFetch!([])
    await loadPromise

    expect(store.loading).toBe(false)
  })

  it('removes a device from state after revoking it', async () => {
    vi.mocked(authApi.fetchTrustedDevices).mockResolvedValue([
      {
        id: '1',
        label: 'Chrome em Windows',
        lastUsedAt: '2026-08-01T10:00:00Z',
        expiresAt: '2026-08-31T10:00:00Z',
      },
      {
        id: '2',
        label: 'Safari em iOS',
        lastUsedAt: '2026-08-02T10:00:00Z',
        expiresAt: '2026-09-01T10:00:00Z',
      },
    ])
    vi.mocked(authApi.revokeTrustedDevice).mockResolvedValue()

    const store = useTrustedDevicesStore()
    await store.load()
    await store.revoke('1')

    expect(store.devices.map((device) => device.id)).toEqual(['2'])
    expect(authApi.revokeTrustedDevice).toHaveBeenCalledWith('1')
  })
})
