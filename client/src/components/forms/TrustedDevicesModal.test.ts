import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { createTestingPinia, type TestingPinia } from '@pinia/testing'
import TrustedDevicesModal from './TrustedDevicesModal.vue'
import { authApi } from '@/api/auth'

vi.mock('@/api/auth', () => ({
  authApi: {
    fetchTrustedDevices: vi.fn(),
    revokeTrustedDevice: vi.fn(),
  },
}))

let activeWrapper: VueWrapper | undefined
let pinia: TestingPinia

function mountModal() {
  activeWrapper = mount(TrustedDevicesModal, {
    global: { plugins: [pinia] },
    attachTo: document.body,
  })
  return activeWrapper
}

describe('TrustedDevicesModal', () => {
  beforeEach(() => {
    pinia = createTestingPinia({ stubActions: false })
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

  it('asks for confirmation, then revokes the device and removes it from the list', async () => {
    vi.mocked(authApi.fetchTrustedDevices).mockResolvedValue([
      {
        id: '1',
        label: 'Chrome em Windows',
        lastUsedAt: '2026-08-01T10:00:00Z',
        expiresAt: '2026-08-31T10:00:00Z',
      },
      {
        id: '2',
        label: 'Safari em iPhone',
        lastUsedAt: '2026-08-02T10:00:00Z',
        expiresAt: '2026-09-01T10:00:00Z',
      },
    ])
    vi.mocked(authApi.revokeTrustedDevice).mockResolvedValue()

    const wrapper = mountModal()
    await flushPromises()
    await wrapper.findAll('button[aria-label="Revogar dispositivo"]')[0].trigger('click')
    await flushPromises()

    expect(authApi.revokeTrustedDevice).not.toHaveBeenCalled()
    expect(document.body.textContent).toContain('Revogar Chrome em Windows?')

    const confirmButton = Array.from(document.body.querySelectorAll('button')).find(
      (button) => button.textContent?.trim() === 'Revogar',
    )!
    confirmButton.click()
    await flushPromises()

    expect(authApi.revokeTrustedDevice).toHaveBeenCalledWith('1')
    expect(document.body.textContent).not.toContain('Chrome em Windows')
    expect(document.body.textContent).toContain('Safari em iPhone')
  })

  it('keeps the device when the confirmation is cancelled', async () => {
    vi.mocked(authApi.fetchTrustedDevices).mockResolvedValue([
      {
        id: '1',
        label: 'Chrome em Windows',
        lastUsedAt: '2026-08-01T10:00:00Z',
        expiresAt: '2026-08-31T10:00:00Z',
      },
    ])

    const wrapper = mountModal()
    await flushPromises()
    await wrapper.find('button[aria-label="Revogar dispositivo"]').trigger('click')
    await flushPromises()

    const cancelButton = Array.from(document.body.querySelectorAll('button')).find(
      (button) => button.textContent?.trim() === 'Cancelar',
    )!
    cancelButton.click()
    await flushPromises()

    expect(authApi.revokeTrustedDevice).not.toHaveBeenCalled()
    expect(document.body.textContent).toContain('Chrome em Windows')
  })

  it('emits close from the footer button', async () => {
    vi.mocked(authApi.fetchTrustedDevices).mockResolvedValue([])

    const wrapper = mountModal()
    await flushPromises()
    const closeButton = wrapper.findAll('button').find((button) => button.text() === 'Fechar')!
    await closeButton.trigger('click')

    expect(wrapper.emitted('close')).toHaveLength(1)
  })
})

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}
