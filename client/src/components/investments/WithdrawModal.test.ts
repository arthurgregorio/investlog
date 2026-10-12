import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import WithdrawModal from './WithdrawModal.vue'
import { resultsApi } from '@/api/results'
import type { WalletKind } from '@/types'

vi.mock('@/api/results', () => ({
  resultsApi: {
    findAll: vi.fn(),
    withdrawFromStockHolding: vi.fn(),
    withdrawFromCryptoHolding: vi.fn(),
    withdrawFromFundHolding: vi.fn(),
  },
}))

function mountModal(
  overrides: {
    kind?: WalletKind
    remainingQuantity?: number | null
    currentValue?: number | null
  } = {},
) {
  return mount(WithdrawModal, {
    props: {
      holdingId: 'holding-1',
      walletId: 'wallet-1',
      kind: overrides.kind ?? 'STOCKS',
      walletCurrency: 'BRL',
      remainingQuantity:
        overrides.remainingQuantity === undefined ? 100 : overrides.remainingQuantity,
      currentValue: overrides.currentValue === undefined ? 3850 : overrides.currentValue,
    },
  })
}

function numberInputs(wrapper: ReturnType<typeof mountModal>) {
  return wrapper.findAll('input[type="number"]')
}

function clickSubmit(wrapper: ReturnType<typeof mountModal>) {
  return wrapper.findAll('button').slice(-1)[0].trigger('click')
}

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

describe('WithdrawModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('asks for quantity and unit price on a stock holding', () => {
    const wrapper = mountModal()

    expect(wrapper.text()).toContain('Quantidade')
    expect(wrapper.text()).toContain('Preço unitário')
    expect(wrapper.text()).not.toContain('Valor resgatado')
  })

  it('asks for an amount on a fund holding, with no quantity field', () => {
    const wrapper = mountModal({ kind: 'FUNDS' })

    expect(wrapper.text()).toContain('Valor resgatado')
    expect(wrapper.text()).not.toContain('Quantidade')
    expect(wrapper.text()).not.toContain('Preço unitário')
  })

  it('submits a partial stock withdrawal with fees and taxes sent as 0 when left empty', async () => {
    vi.mocked(resultsApi.withdrawFromStockHolding).mockResolvedValue()
    const wrapper = mountModal({ remainingQuantity: 100 })

    const inputs = numberInputs(wrapper)
    await inputs[0].setValue('40')
    await inputs[1].setValue('50')
    await clickSubmit(wrapper)
    await flushPromises()

    expect(resultsApi.withdrawFromStockHolding).toHaveBeenCalledWith(
      'wallet-1',
      'holding-1',
      expect.objectContaining({ quantity: 40, unitPrice: 50, fees: 0, taxes: 0 }),
    )
  })

  it('reports a partial exit as not completing the position', async () => {
    vi.mocked(resultsApi.withdrawFromStockHolding).mockResolvedValue()
    const wrapper = mountModal({ remainingQuantity: 100 })

    const inputs = numberInputs(wrapper)
    await inputs[0].setValue('40')
    await inputs[1].setValue('50')
    await clickSubmit(wrapper)
    await flushPromises()

    expect(wrapper.emitted('withdrawn')?.slice(-1)[0]).toEqual([false])
  })

  it('reports exiting the whole remaining quantity as completing the position', async () => {
    vi.mocked(resultsApi.withdrawFromStockHolding).mockResolvedValue()
    const wrapper = mountModal({ remainingQuantity: 100 })

    const inputs = numberInputs(wrapper)
    await inputs[0].setValue('100')
    await inputs[1].setValue('50')
    await clickSubmit(wrapper)
    await flushPromises()

    expect(wrapper.emitted('withdrawn')?.slice(-1)[0]).toEqual([true])
  })

  it('sends a fund withdrawal as an amount', async () => {
    vi.mocked(resultsApi.withdrawFromFundHolding).mockResolvedValue()
    const wrapper = mountModal({ kind: 'FUNDS', currentValue: 10000 })

    const inputs = numberInputs(wrapper)
    await inputs[0].setValue('2500')
    await clickSubmit(wrapper)
    await flushPromises()

    expect(resultsApi.withdrawFromFundHolding).toHaveBeenCalledWith(
      'wallet-1',
      'holding-1',
      expect.objectContaining({ amount: 2500, fees: 0, taxes: 0 }),
    )
  })

  it('routes a crypto holding to the crypto endpoint', async () => {
    vi.mocked(resultsApi.withdrawFromCryptoHolding).mockResolvedValue()
    const wrapper = mountModal({ kind: 'CRYPTO', remainingQuantity: 2 })

    const inputs = numberInputs(wrapper)
    await inputs[0].setValue('1')
    await inputs[1].setValue('95000')
    await clickSubmit(wrapper)
    await flushPromises()

    expect(resultsApi.withdrawFromCryptoHolding).toHaveBeenCalled()
    expect(resultsApi.withdrawFromStockHolding).not.toHaveBeenCalled()
  })

  it("shows the server's rejection message and keeps the entered values", async () => {
    vi.mocked(resultsApi.withdrawFromStockHolding).mockRejectedValue({
      isAxiosError: true,
      response: {
        status: 400,
        data: { detail: 'A quantidade resgatada não pode ser maior que a quantidade restante' },
      },
    })
    const wrapper = mountModal({ remainingQuantity: 10 })

    const inputs = numberInputs(wrapper)
    await inputs[0].setValue('11')
    await inputs[1].setValue('25')
    await clickSubmit(wrapper)
    await flushPromises()

    expect(wrapper.find('[data-testid="withdraw-error"]').text()).toContain(
      'não pode ser maior que a quantidade restante',
    )
    expect(wrapper.emitted('close')).toBeUndefined()
    expect((numberInputs(wrapper)[0].element as HTMLInputElement).value).toBe('11')
  })

  it('shows a validation message list when the server rejects the fields', async () => {
    vi.mocked(resultsApi.withdrawFromStockHolding).mockRejectedValue({
      isAxiosError: true,
      response: { status: 400, data: { errors: ['A data do resgate é obrigatória'] } },
    })
    const wrapper = mountModal()

    const inputs = numberInputs(wrapper)
    await inputs[0].setValue('1')
    await inputs[1].setValue('1')
    await clickSubmit(wrapper)
    await flushPromises()

    expect(wrapper.find('[data-testid="withdraw-error"]').text()).toBe(
      'A data do resgate é obrigatória',
    )
  })

  describe('"Tudo" checkbox', () => {
    function inputByTestId(wrapper: ReturnType<typeof mountModal>, testId: string) {
      const element = wrapper.get(`[data-testid="${testId}"]`)
      return element.element.tagName === 'INPUT' ? element : element.get('input')
    }

    function wholePositionCheckbox(wrapper: ReturnType<typeof mountModal>) {
      return inputByTestId(wrapper, 'withdraw-whole-position')
    }

    it('fills and locks the quantity with the remaining quantity of a stock holding', async () => {
      const wrapper = mountModal({ remainingQuantity: 100 })

      await wholePositionCheckbox(wrapper).setValue(true)

      const quantityInput = inputByTestId(wrapper, 'withdraw-quantity')
      expect((quantityInput.element as HTMLInputElement).value).toBe('100')
      expect(quantityInput.attributes('disabled')).toBeDefined()
    })

    it('fills and locks the amount with the current value of a fund', async () => {
      const wrapper = mountModal({ kind: 'FUNDS', currentValue: 1234.56 })

      await wholePositionCheckbox(wrapper).setValue(true)

      const amountInput = inputByTestId(wrapper, 'withdraw-amount')
      expect((amountInput.element as HTMLInputElement).value).toBe('1234.56')
      expect(amountInput.attributes('disabled')).toBeDefined()
    })

    it('re-enables the field and keeps the value when unchecked', async () => {
      const wrapper = mountModal({ remainingQuantity: 100 })

      await wholePositionCheckbox(wrapper).setValue(true)
      await wholePositionCheckbox(wrapper).setValue(false)

      const quantityInput = inputByTestId(wrapper, 'withdraw-quantity')
      expect(quantityInput.attributes('disabled')).toBeUndefined()
      expect((quantityInput.element as HTMLInputElement).value).toBe('100')
    })

    it.each<[WalletKind, { remainingQuantity?: null; currentValue?: null }]>([
      ['STOCKS', { remainingQuantity: null }],
      ['FUNDS', { currentValue: null }],
    ])('is disabled on a %s holding whose available amount is unknown', (kind, unknown) => {
      const wrapper = mountModal({ kind, ...unknown })

      expect(wholePositionCheckbox(wrapper).attributes('disabled')).toBeDefined()
    })

    it('records a stock withdrawal of the whole position as a full exit', async () => {
      vi.mocked(resultsApi.withdrawFromStockHolding).mockResolvedValue()
      const wrapper = mountModal({ remainingQuantity: 100 })

      await wholePositionCheckbox(wrapper).setValue(true)
      await numberInputs(wrapper)[1].setValue('50')
      await clickSubmit(wrapper)
      await flushPromises()

      expect(resultsApi.withdrawFromStockHolding).toHaveBeenCalledWith(
        'wallet-1',
        'holding-1',
        expect.objectContaining({ quantity: 100, unitPrice: 50 }),
      )
      expect(wrapper.emitted('withdrawn')).toEqual([[true]])
    })

    it('records a fund withdrawal of the whole value as a full exit', async () => {
      vi.mocked(resultsApi.withdrawFromFundHolding).mockResolvedValue()
      const wrapper = mountModal({ kind: 'FUNDS', currentValue: 1234.56 })

      await wholePositionCheckbox(wrapper).setValue(true)
      await clickSubmit(wrapper)
      await flushPromises()

      expect(resultsApi.withdrawFromFundHolding).toHaveBeenCalledWith(
        'wallet-1',
        'holding-1',
        expect.objectContaining({ amount: 1234.56 }),
      )
      expect(wrapper.emitted('withdrawn')).toEqual([[true]])
    })
  })
})
