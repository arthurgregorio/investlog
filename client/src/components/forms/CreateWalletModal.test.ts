import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { createTestingPinia, type TestingPinia } from '@pinia/testing'
import CreateWalletModal from './CreateWalletModal.vue'
import { walletsApi } from '@/api/wallets'
import { useWalletsStore } from '@/stores/wallets'
import type { WalletKind, WalletResponse } from '@/types'

vi.mock('@/api/wallets', () => ({
  walletsApi: {
    create: vi.fn(),
  },
}))

let activeWrapper: VueWrapper | undefined

function createdWallet(): WalletResponse {
  return {
    id: 'wallet-new',
    name: 'Dividendos',
    kind: 'STOCKS',
    currency: 'BRL',
    holdingCount: 0,
    totalInvested: 0,
    currentValue: null,
    gain: null,
    gainPct: null,
    createdAt: '2026-01-01T00:00:00Z',
  }
}

function mountModal(
  options: { initialType?: WalletKind; rates?: { currencyCode: string; isBase: boolean }[] } = {},
) {
  const pinia: TestingPinia = createTestingPinia({
    initialState: {
      rates: {
        rates: (options.rates ?? []).map((rate) => ({ ...rate, rate: 1 })),
      },
    },
  })
  activeWrapper = mount(CreateWalletModal, {
    props: { initialType: options.initialType },
    global: { plugins: [pinia], config: { errorHandler: () => undefined } },
    attachTo: document.body,
  })
  return activeWrapper
}

function submitButton(wrapper: VueWrapper) {
  return wrapper.findAll('button').find((button) => button.text().includes('Criar carteira'))!
}

function cancelButton(wrapper: VueWrapper) {
  return wrapper.findAll('button').find((button) => button.text().includes('Cancelar'))!
}

function nameInput(wrapper: VueWrapper) {
  return wrapper.find('input[placeholder="ex.: Carteira de Dividendos"]')
}

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

describe('CreateWalletModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    activeWrapper?.unmount()
    activeWrapper = undefined
  })

  it('keeps the submit button disabled while the name is empty or blank', async () => {
    const wrapper = mountModal()

    expect(submitButton(wrapper).attributes('disabled')).toBeDefined()

    await nameInput(wrapper).setValue('   ')
    expect(submitButton(wrapper).attributes('disabled')).toBeDefined()

    await nameInput(wrapper).setValue('Dividendos')
    expect(submitButton(wrapper).attributes('disabled')).toBeUndefined()
  })

  it('offers the three wallet kinds and the fallback currencies when no rate is loaded', () => {
    const wrapper = mountModal()

    expect(document.body.textContent).toContain('Ações')
    expect(document.body.textContent).toContain('Cripto')
    expect(document.body.textContent).toContain('Fundos')
    const currencies = wrapper.findAll('select option').map((option) => option.text())
    expect(currencies).toEqual(['BRL', 'USD', 'EUR'])
  })

  it('lists the currencies from the rates store and defaults to the base currency', async () => {
    vi.mocked(walletsApi.create).mockResolvedValue(createdWallet())
    const wrapper = mountModal({
      rates: [
        { currencyCode: 'BRL', isBase: false },
        { currencyCode: 'CHF', isBase: true },
      ],
    })

    expect(wrapper.findAll('select option').map((option) => option.text())).toEqual(['BRL', 'CHF'])

    await nameInput(wrapper).setValue('Suíça')
    await submitButton(wrapper).trigger('click')
    await flushPromises()

    expect(walletsApi.create).toHaveBeenCalledWith({
      name: 'Suíça',
      kind: 'STOCKS',
      currency: 'CHF',
    })
  })

  it('creates the wallet with the trimmed name, refreshes the store and announces it', async () => {
    vi.mocked(walletsApi.create).mockResolvedValue(createdWallet())
    const wrapper = mountModal()

    await nameInput(wrapper).setValue('  Dividendos  ')
    await submitButton(wrapper).trigger('click')
    await flushPromises()

    expect(walletsApi.create).toHaveBeenCalledWith({
      name: 'Dividendos',
      kind: 'STOCKS',
      currency: 'BRL',
    })
    expect(useWalletsStore().refresh).toHaveBeenCalled()
    expect(wrapper.emitted('created')).toEqual([['wallet-new', 'STOCKS']])
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('starts on the kind passed in and creates a wallet of the kind the user picks', async () => {
    vi.mocked(walletsApi.create).mockResolvedValue(createdWallet())
    const wrapper = mountModal({ initialType: 'CRYPTO' })

    const radios = wrapper.findAll('input[type="radio"]')
    expect((radios[1].element as HTMLInputElement).checked).toBe(true)

    await radios[2].setValue(true)
    await nameInput(wrapper).setValue('Renda fixa')
    await submitButton(wrapper).trigger('click')
    await flushPromises()

    expect(walletsApi.create).toHaveBeenCalledWith(expect.objectContaining({ kind: 'FUNDS' }))
    expect(wrapper.emitted('created')).toEqual([['wallet-new', 'FUNDS']])
  })

  it('stays open and lets the user retry when the create is rejected', async () => {
    vi.mocked(walletsApi.create).mockRejectedValueOnce(new Error('network'))
    const wrapper = mountModal()

    await nameInput(wrapper).setValue('Dividendos')
    await submitButton(wrapper).trigger('click')
    await flushPromises()

    expect(wrapper.emitted('close')).toBeUndefined()
    expect(wrapper.emitted('created')).toBeUndefined()
    expect(useWalletsStore().refresh).not.toHaveBeenCalled()
    expect(submitButton(wrapper).attributes('disabled')).toBeUndefined()

    vi.mocked(walletsApi.create).mockResolvedValueOnce(createdWallet())
    await submitButton(wrapper).trigger('click')
    await flushPromises()

    expect(walletsApi.create).toHaveBeenCalledTimes(2)
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('does not call the API when submitted with an empty name', async () => {
    const wrapper = mountModal()

    await submitButton(wrapper).trigger('click')
    await flushPromises()

    expect(walletsApi.create).not.toHaveBeenCalled()
  })

  it('closes without creating anything when cancelled', async () => {
    const wrapper = mountModal()

    await cancelButton(wrapper).trigger('click')

    expect(wrapper.emitted('close')).toHaveLength(1)
    expect(walletsApi.create).not.toHaveBeenCalled()
  })
})
