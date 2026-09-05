import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import TrustedDevicesModal from './TrustedDevicesModal.vue'
import { authApi } from '@/api/auth'

vi.mock('@/api/auth', () => ({
  authApi: {
    fetchTrustedDevices: vi.fn(),
    revokeTrustedDevice: vi.fn(),
  },
}))

let activeWrapper: VueWrapper | undefined

function mountModal() {
  activeWrapper = mount(TrustedDevicesModal, {
    attachTo: document.body,
  })
  return activeWrapper
}

describe('TrustedDevicesModal', () => {
  beforeEach(() => {
    createTestingPinia({ stubActions: false })
    vi.clearAllMocks()
  })

  afterEach(() => {
    activeWrapper?.unmount()
    activeWrapper = undefined
  })

  it('lists the loaded devices', async () => {
    vi.mocked(authApi.fetchTrustedDevices).mockResolvedValue([
      {
        id: '1',
        label: 'Chrome em Windows',
        lastUsedAt: '2026-08-01T10:00:00Z',
        expiresAt: '2026-08-31T10:00:00Z',
      },
    ])

    mountModal()
    await flushPromises()

    expect(document.body.textContent).toContain('Chrome em Windows')
  })

  it('shows an empty state when there are no trusted devices', async () => {
    vi.mocked(authApi.fetchTrustedDevices).mockResolvedValue([])

    mountModal()
    await flushPromises()

    expect(document.body.textContent).toContain('Nenhum dispositivo confiável')
  })
})

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}
