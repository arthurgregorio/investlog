import { describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'
import { ModalKey, useModals, type ModalControls } from './useModals'

const Consumer = defineComponent({
  setup() {
    const modals = useModals()
    return () =>
      h('div', [
        h('button', { id: 'add-investment', onClick: () => modals.openAddInvestment('CRYPTO') }, 'a'),
        h('button', { id: 'create-wallet', onClick: () => modals.openCreateWallet('FUNDS') }, 'b'),
        h('button', { id: 'password', onClick: () => modals.openPasswordChange() }, 'c'),
        h('button', { id: 'devices', onClick: () => modals.openTrustedDevices() }, 'd'),
      ])
  },
})

describe('useModals', () => {
  it('hands the provided controls to the component that injects them', async () => {
    const controls: ModalControls = {
      openAddInvestment: vi.fn(),
      openCreateWallet: vi.fn(),
      openPasswordChange: vi.fn(),
      openTrustedDevices: vi.fn(),
    }
    const wrapper = mount(Consumer, { global: { provide: { [ModalKey as symbol]: controls } } })

    await wrapper.get('#add-investment').trigger('click')
    await wrapper.get('#create-wallet').trigger('click')
    await wrapper.get('#password').trigger('click')
    await wrapper.get('#devices').trigger('click')

    expect(controls.openAddInvestment).toHaveBeenCalledWith('CRYPTO')
    expect(controls.openCreateWallet).toHaveBeenCalledWith('FUNDS')
    expect(controls.openPasswordChange).toHaveBeenCalledTimes(1)
    expect(controls.openTrustedDevices).toHaveBeenCalledTimes(1)
  })

  it('throws when no provider is above the component', () => {
    vi.spyOn(console, 'warn').mockImplementation(() => undefined)

    expect(() => mount(Consumer)).toThrow('useModals() must be used within the App modal provider')

    vi.restoreAllMocks()
  })
})
