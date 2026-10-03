import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import UpdatePriceModal from './UpdatePriceModal.vue'
import { holdingsApi } from '@/api/holdings'
import type { WalletKind } from '@/types'

vi.mock('@/api/holdings', () => ({
  holdingsApi: {
    updateStockHolding: vi.fn(),
    updateCryptoHolding: vi.fn(),
    updateFundHolding: vi.fn(),
  },
}))

let activeWrapper: VueWrapper | undefined

function mountModal(
  options: { kind?: WalletKind; walletCurrency?: string; initialValue?: number | null } = {},
) {
  activeWrapper = mount(UpdatePriceModal, {
    props: {
      holdingId: 'holding-1',
      walletId: 'wallet-1',
      kind: options.kind ?? 'STOCKS',
      walletCurrency: options.walletCurrency ?? 'BRL',
      initialValue: options.initialValue === undefined ? 38.5 : options.initialValue,
    },
    global: { config: { errorHandler: () => undefined } },
    attachTo: document.body,
  })
  return activeWrapper
}

function priceInput(wrapper: VueWrapper) {
  return wrapper.find('input[type="number"]')
}

function buttonLabelled(wrapper: VueWrapper, label: string) {
  return wrapper.findAll('button').find((button) => button.text() === label)!
}

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

describe('UpdatePriceModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    activeWrapper?.unmount()
    activeWrapper = undefined
  })

  it('pre-fills the current price of the holding', () => {
    const wrapper = mountModal({ initialValue: 38.5 })

    expect((priceInput(wrapper).element as HTMLInputElement).value).toBe('38.5')
    expect(buttonLabelled(wrapper, 'Salvar').attributes('disabled')).toBeUndefined()
  })

  it('starts empty and disabled when the holding has no price yet', () => {
    const wrapper = mountModal({ initialValue: null })

    expect((priceInput(wrapper).element as HTMLInputElement).value).toBe('')
    expect(buttonLabelled(wrapper, 'Salvar').attributes('disabled')).toBeDefined()
  })

  it.each<WalletKind>(['STOCKS', 'CRYPTO'])('labels the field as a price on a %s holding', (kind) => {
    mountModal({ kind })

    expect(document.body.textContent).toContain('Atualizar preço')
    expect(document.body.textContent).toContain('Informe o preço atual do ativo.')
    expect(document.body.textContent).toContain('Preço atual')
  })

  it('labels the field as a value on a fund holding', () => {
    mountModal({ kind: 'FUNDS' })

    expect(document.body.textContent).toContain('Atualizar valor atual')
    expect(document.body.textContent).toContain('Informe o valor atual do fundo.')
  })

  it('prefixes the field with the symbol of the wallet currency', () => {
    const wrapper = mountModal({ walletCurrency: 'USD' })

    expect(wrapper.find('.button.is-static').text()).toBe('US$')
  })

  it('disables Salvar when the field is cleared or negative', async () => {
    const wrapper = mountModal()

    await priceInput(wrapper).setValue('')
    expect(buttonLabelled(wrapper, 'Salvar').attributes('disabled')).toBeDefined()

    await priceInput(wrapper).setValue('-1')
    expect(buttonLabelled(wrapper, 'Salvar').attributes('disabled')).toBeDefined()

    await priceInput(wrapper).setValue('12')
    expect(buttonLabelled(wrapper, 'Salvar').attributes('disabled')).toBeUndefined()
  })

  it('accepts a zero price, which the component treats as valid', async () => {
    vi.mocked(holdingsApi.updateStockHolding).mockResolvedValue({} as never)
    const wrapper = mountModal()

    await priceInput(wrapper).setValue('0')
    await buttonLabelled(wrapper, 'Salvar').trigger('click')
    await flushPromises()

    expect(holdingsApi.updateStockHolding).toHaveBeenCalledWith('wallet-1', 'holding-1', {
      currentPrice: 0,
    })
  })

  it('updates a stock price, then announces it and closes', async () => {
    vi.mocked(holdingsApi.updateStockHolding).mockResolvedValue({} as never)
    const wrapper = mountModal({ kind: 'STOCKS' })

    await priceInput(wrapper).setValue('41.25')
    await buttonLabelled(wrapper, 'Salvar').trigger('click')
    await flushPromises()

    expect(holdingsApi.updateStockHolding).toHaveBeenCalledWith('wallet-1', 'holding-1', {
      currentPrice: 41.25,
    })
    expect(wrapper.emitted('updated')).toHaveLength(1)
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('routes a crypto price to the crypto endpoint', async () => {
    vi.mocked(holdingsApi.updateCryptoHolding).mockResolvedValue({} as never)
    const wrapper = mountModal({ kind: 'CRYPTO', initialValue: 300000 })

    await priceInput(wrapper).setValue('312000')
    await buttonLabelled(wrapper, 'Salvar').trigger('click')
    await flushPromises()

    expect(holdingsApi.updateCryptoHolding).toHaveBeenCalledWith('wallet-1', 'holding-1', {
      currentPrice: 312000,
    })
    expect(holdingsApi.updateStockHolding).not.toHaveBeenCalled()
    expect(wrapper.emitted('updated')).toHaveLength(1)
  })

  it('sends a fund update as a current value', async () => {
    vi.mocked(holdingsApi.updateFundHolding).mockResolvedValue({} as never)
    const wrapper = mountModal({ kind: 'FUNDS', initialValue: 1000 })

    await priceInput(wrapper).setValue('1080.5')
    await buttonLabelled(wrapper, 'Salvar').trigger('click')
    await flushPromises()

    expect(holdingsApi.updateFundHolding).toHaveBeenCalledWith('wallet-1', 'holding-1', {
      currentValue: 1080.5,
    })
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('stays open without announcing anything when the update is rejected', async () => {
    vi.mocked(holdingsApi.updateStockHolding).mockRejectedValue(new Error('network'))
    const wrapper = mountModal()

    await buttonLabelled(wrapper, 'Salvar').trigger('click')
    await flushPromises()

    expect(wrapper.emitted('updated')).toBeUndefined()
    expect(wrapper.emitted('close')).toBeUndefined()
    expect(buttonLabelled(wrapper, 'Salvar').attributes('disabled')).toBeUndefined()
  })

  it('does not call the API when submitted while the field is empty', async () => {
    const wrapper = mountModal({ initialValue: null })

    await buttonLabelled(wrapper, 'Salvar').trigger('click')
    await flushPromises()

    expect(holdingsApi.updateStockHolding).not.toHaveBeenCalled()
  })

  it('closes without saving when cancelled', async () => {
    const wrapper = mountModal()

    await buttonLabelled(wrapper, 'Cancelar').trigger('click')

    expect(wrapper.emitted('close')).toHaveLength(1)
    expect(holdingsApi.updateStockHolding).not.toHaveBeenCalled()
  })
})
