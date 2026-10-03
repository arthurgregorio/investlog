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
  options: {
    kind?: WalletKind
    walletCurrency?: string
    initialValue?: number | null
    initialAdministrationFeeRate?: number | null
    initialPerformanceFeeRate?: number | null
  } = {},
) {
  activeWrapper = mount(UpdatePriceModal, {
    props: {
      holdingId: 'holding-1',
      walletId: 'wallet-1',
      kind: options.kind ?? 'STOCKS',
      walletCurrency: options.walletCurrency ?? 'BRL',
      initialValue: options.initialValue === undefined ? 38.5 : options.initialValue,
      initialAdministrationFeeRate: options.initialAdministrationFeeRate,
      initialPerformanceFeeRate: options.initialPerformanceFeeRate,
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

    expect(document.body.textContent).toContain('Atualizar fundo')
    expect(document.body.textContent).toContain('Informe o valor atual e as taxas do fundo.')
    expect(document.body.textContent).toContain('Valor atual')
  })

  it.each<WalletKind>(['STOCKS', 'CRYPTO'])(
    'offers a single field and no fee rates on a %s holding',
    (kind) => {
      const wrapper = mountModal({ kind, initialAdministrationFeeRate: 1.5 })

      expect(wrapper.findAll('input[type="number"]')).toHaveLength(1)
      expect(document.body.textContent).not.toContain('Taxa de administração')
      expect(document.body.textContent).not.toContain('Taxa de performance')
    },
  )

  describe('fund fee rates', () => {
    function fundInputs(wrapper: VueWrapper) {
      const [valueInput, administrationInput, performanceInput] = wrapper.findAll(
        'input[type="number"]',
      )
      return { valueInput, administrationInput, performanceInput }
    }

    function fundPayload() {
      return vi.mocked(holdingsApi.updateFundHolding).mock.calls[0][2]
    }

    function inputValue(input: ReturnType<VueWrapper['find']>) {
      return (input.element as HTMLInputElement).value
    }

    beforeEach(() => {
      vi.mocked(holdingsApi.updateFundHolding).mockResolvedValue({} as never)
    })

    it('adds the two rate fields below the current value, with their units in the labels', () => {
      const wrapper = mountModal({ kind: 'FUNDS', initialValue: 1000 })

      expect(wrapper.findAll('label.label').map((label) => label.text())).toEqual([
        'Valor atual',
        'Taxa de administração (% a.a.)',
        'Taxa de performance (%)',
      ])
      expect(wrapper.findAll('.button.is-static')).toHaveLength(1)
    })

    it('pre-fills the stored rates', () => {
      const wrapper = mountModal({
        kind: 'FUNDS',
        initialValue: 1000,
        initialAdministrationFeeRate: 1.5,
        initialPerformanceFeeRate: 20,
      })

      const { valueInput, administrationInput, performanceInput } = fundInputs(wrapper)
      expect(inputValue(valueInput)).toBe('1000')
      expect(inputValue(administrationInput)).toBe('1.5')
      expect(inputValue(performanceInput)).toBe('20')
    })

    it('starts the rate fields empty when the fund has no rates', () => {
      const wrapper = mountModal({
        kind: 'FUNDS',
        initialValue: 1000,
        initialAdministrationFeeRate: null,
        initialPerformanceFeeRate: null,
      })

      const { administrationInput, performanceInput } = fundInputs(wrapper)
      expect(inputValue(administrationInput)).toBe('')
      expect(inputValue(performanceInput)).toBe('')
    })

    it('saves an edited administration rate and keeps the current value and the other rate', async () => {
      const wrapper = mountModal({
        kind: 'FUNDS',
        initialValue: 1000,
        initialAdministrationFeeRate: 1.5,
        initialPerformanceFeeRate: 20,
      })

      await fundInputs(wrapper).administrationInput.setValue('2.25')
      await buttonLabelled(wrapper, 'Salvar').trigger('click')
      await flushPromises()

      expect(holdingsApi.updateFundHolding).toHaveBeenCalledWith('wallet-1', 'holding-1', {
        currentValue: 1000,
        administrationFeeRate: 2.25,
        performanceFeeRate: 20,
      })
      expect(wrapper.emitted('updated')).toHaveLength(1)
      expect(wrapper.emitted('close')).toHaveLength(1)
    })

    it('saves an edited performance rate and keeps the current value and the other rate', async () => {
      const wrapper = mountModal({
        kind: 'FUNDS',
        initialValue: 1000,
        initialAdministrationFeeRate: 1.5,
        initialPerformanceFeeRate: 20,
      })

      await fundInputs(wrapper).performanceInput.setValue('10')
      await buttonLabelled(wrapper, 'Salvar').trigger('click')
      await flushPromises()

      expect(holdingsApi.updateFundHolding).toHaveBeenCalledWith('wallet-1', 'holding-1', {
        currentValue: 1000,
        administrationFeeRate: 1.5,
        performanceFeeRate: 10,
      })
    })

    it('fills in the rates of a fund that had none while editing the value', async () => {
      const wrapper = mountModal({ kind: 'FUNDS', initialValue: 1000 })

      const { valueInput, administrationInput, performanceInput } = fundInputs(wrapper)
      await valueInput.setValue('1100')
      await administrationInput.setValue('1')
      await performanceInput.setValue('15')
      await buttonLabelled(wrapper, 'Salvar').trigger('click')
      await flushPromises()

      expect(holdingsApi.updateFundHolding).toHaveBeenCalledWith('wallet-1', 'holding-1', {
        currentValue: 1100,
        administrationFeeRate: 1,
        performanceFeeRate: 15,
      })
    })

    it('leaves blank rates out of the payload', async () => {
      const wrapper = mountModal({ kind: 'FUNDS', initialValue: 1000 })

      await fundInputs(wrapper).valueInput.setValue('1080')
      await buttonLabelled(wrapper, 'Salvar').trigger('click')
      await flushPromises()

      expect(fundPayload()).toEqual({ currentValue: 1080 })
      expect(fundPayload()).not.toHaveProperty('administrationFeeRate')
      expect(fundPayload()).not.toHaveProperty('performanceFeeRate')
    })

    it('sends a rate of zero instead of dropping it', async () => {
      const wrapper = mountModal({
        kind: 'FUNDS',
        initialValue: 1000,
        initialAdministrationFeeRate: 1.5,
        initialPerformanceFeeRate: 20,
      })

      const { administrationInput, performanceInput } = fundInputs(wrapper)
      await administrationInput.setValue('0')
      await performanceInput.setValue('0')
      await buttonLabelled(wrapper, 'Salvar').trigger('click')
      await flushPromises()

      expect(fundPayload()).toHaveProperty('administrationFeeRate', 0)
      expect(fundPayload()).toHaveProperty('performanceFeeRate', 0)
    })

    it('saves a rate on a fund with no current value without sending one', async () => {
      const wrapper = mountModal({ kind: 'FUNDS', initialValue: null })
      expect(buttonLabelled(wrapper, 'Salvar').attributes('disabled')).toBeDefined()

      await fundInputs(wrapper).administrationInput.setValue('1.5')
      expect(buttonLabelled(wrapper, 'Salvar').attributes('disabled')).toBeUndefined()
      await buttonLabelled(wrapper, 'Salvar').trigger('click')
      await flushPromises()

      expect(fundPayload()).toEqual({ administrationFeeRate: 1.5 })
      expect(fundPayload()).not.toHaveProperty('currentValue')
    })

    it('saves a current value on its own when the rates stay blank', async () => {
      const wrapper = mountModal({ kind: 'FUNDS', initialValue: null })

      await fundInputs(wrapper).valueInput.setValue('500')
      expect(buttonLabelled(wrapper, 'Salvar').attributes('disabled')).toBeUndefined()
    })

    it('disables Salvar when every field is blank', async () => {
      const wrapper = mountModal({
        kind: 'FUNDS',
        initialValue: 1000,
        initialAdministrationFeeRate: 1.5,
        initialPerformanceFeeRate: 20,
      })

      const { valueInput, administrationInput, performanceInput } = fundInputs(wrapper)
      await valueInput.setValue('')
      await administrationInput.setValue('')
      await performanceInput.setValue('')

      expect(buttonLabelled(wrapper, 'Salvar').attributes('disabled')).toBeDefined()
    })

    it.each([
      ['administration', 1],
      ['performance', 2],
    ])('disables Salvar when the %s rate is negative', async (_label, index) => {
      const wrapper = mountModal({ kind: 'FUNDS', initialValue: 1000 })

      await wrapper.findAll('input[type="number"]')[index].setValue('-1')

      expect(buttonLabelled(wrapper, 'Salvar').attributes('disabled')).toBeDefined()
    })

    it('does not call the API when submitted while invalid', async () => {
      const wrapper = mountModal({ kind: 'FUNDS', initialValue: null })

      await buttonLabelled(wrapper, 'Salvar').trigger('click')
      await flushPromises()

      expect(holdingsApi.updateFundHolding).not.toHaveBeenCalled()
    })
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
