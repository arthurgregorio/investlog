import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import AddPositionModal, { type PositionEntry } from './AddPositionModal.vue'
import { holdingsApi } from '@/api/holdings'
import type { WalletKind } from '@/types'

vi.mock('@/api/holdings', () => ({
  holdingsApi: {
    addStockLot: vi.fn(),
    addCryptoLot: vi.fn(),
    addContribution: vi.fn(),
    updateStockLot: vi.fn(),
    updateCryptoLot: vi.fn(),
    updateFundContribution: vi.fn(),
  },
}))

let activeWrapper: VueWrapper | undefined

function mountModal(kind: WalletKind = 'STOCKS', walletCurrency = 'BRL') {
  activeWrapper = mount(AddPositionModal, {
    props: { holdingId: 'holding-1', walletId: 'wallet-1', kind, walletCurrency },
    global: { config: { errorHandler: () => undefined } },
    attachTo: document.body,
  })
  return activeWrapper
}

function submitButton(wrapper: VueWrapper, label: string) {
  return wrapper.findAll('button').find((button) => button.text() === label)!
}

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

describe('AddPositionModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-03-15T12:00:00Z'))
  })

  afterEach(() => {
    vi.useRealTimers()
    activeWrapper?.unmount()
    activeWrapper = undefined
  })

  it.each<WalletKind>(['STOCKS', 'CRYPTO'])(
    'titles the modal as a purchase and asks for quantity and price on a %s holding',
    (kind) => {
      mountModal(kind)

      expect(document.body.textContent).toContain('Registrar compra')
      expect(document.body.textContent).toContain('Registre uma nova compra para este ativo.')
      expect(document.body.textContent).toContain('Quantidade')
      expect(document.body.textContent).not.toContain('Valor aportado')
    },
  )

  it('titles the modal as a contribution and asks only for the amount on a fund holding', () => {
    mountModal('FUNDS')

    expect(document.body.textContent).toContain('Registrar aporte')
    expect(document.body.textContent).toContain('Registre um novo aporte neste fundo.')
    expect(document.body.textContent).toContain('Valor aportado')
    expect(document.body.textContent).not.toContain('Quantidade')
  })

  it('prefixes the money fields with the symbol of the wallet currency', () => {
    const wrapper = mountModal('STOCKS', 'EUR')

    expect(wrapper.findAll('.button.is-static').map((prefix) => prefix.text())).toEqual(['€'])
  })

  it('keeps the submit button disabled until the fields are positive', async () => {
    const wrapper = mountModal('STOCKS')
    const [quantityInput, priceInput] = wrapper.findAll('input[type="number"]')

    expect(submitButton(wrapper, 'Registrar compra').attributes('disabled')).toBeDefined()

    await quantityInput.setValue('5')
    await priceInput.setValue('0')
    expect(submitButton(wrapper, 'Registrar compra').attributes('disabled')).toBeDefined()

    await priceInput.setValue('10')
    expect(submitButton(wrapper, 'Registrar compra').attributes('disabled')).toBeUndefined()
  })

  it('registers a stock purchase, then announces it and closes', async () => {
    vi.mocked(holdingsApi.addStockLot).mockResolvedValue({} as never)
    const wrapper = mountModal('STOCKS')
    const [quantityInput, priceInput] = wrapper.findAll('input[type="number"]')

    await quantityInput.setValue('10')
    await priceInput.setValue('36.5')
    await submitButton(wrapper, 'Registrar compra').trigger('click')
    await flushPromises()

    expect(holdingsApi.addStockLot).toHaveBeenCalledWith('wallet-1', 'holding-1', {
      lotDate: '2026-03-15',
      quantity: 10,
      price: 36.5,
    })
    expect(wrapper.emitted('added')).toHaveLength(1)
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('routes a crypto purchase to the crypto lot endpoint', async () => {
    vi.mocked(holdingsApi.addCryptoLot).mockResolvedValue({} as never)
    const wrapper = mountModal('CRYPTO')
    const [quantityInput, priceInput] = wrapper.findAll('input[type="number"]')

    await quantityInput.setValue('0.5')
    await priceInput.setValue('310000')
    await submitButton(wrapper, 'Registrar compra').trigger('click')
    await flushPromises()

    expect(holdingsApi.addCryptoLot).toHaveBeenCalledWith('wallet-1', 'holding-1', {
      lotDate: '2026-03-15',
      quantity: 0.5,
      price: 310000,
    })
    expect(holdingsApi.addStockLot).not.toHaveBeenCalled()
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('registers a fund contribution with the amount', async () => {
    vi.mocked(holdingsApi.addContribution).mockResolvedValue({} as never)
    const wrapper = mountModal('FUNDS')

    await wrapper.find('input[type="number"]').setValue('800')
    await submitButton(wrapper, 'Registrar aporte').trigger('click')
    await flushPromises()

    expect(holdingsApi.addContribution).toHaveBeenCalledWith('wallet-1', 'holding-1', {
      contributionDate: '2026-03-15',
      amount: 800,
    })
    expect(wrapper.emitted('added')).toHaveLength(1)
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('stays open without announcing anything when the request is rejected', async () => {
    vi.mocked(holdingsApi.addStockLot).mockRejectedValue(new Error('network'))
    const wrapper = mountModal('STOCKS')
    const [quantityInput, priceInput] = wrapper.findAll('input[type="number"]')

    await quantityInput.setValue('10')
    await priceInput.setValue('36.5')
    await submitButton(wrapper, 'Registrar compra').trigger('click')
    await flushPromises()

    expect(wrapper.emitted('added')).toBeUndefined()
    expect(wrapper.emitted('close')).toBeUndefined()
    expect(submitButton(wrapper, 'Registrar compra').attributes('disabled')).toBeUndefined()
  })

  it('does not call the API when submitted while invalid', async () => {
    const wrapper = mountModal('STOCKS')

    await submitButton(wrapper, 'Registrar compra').trigger('click')
    await flushPromises()

    expect(holdingsApi.addStockLot).not.toHaveBeenCalled()
  })

  it('closes without saving when cancelled', async () => {
    const wrapper = mountModal('STOCKS')

    await submitButton(wrapper, 'Cancelar').trigger('click')

    expect(wrapper.emitted('close')).toHaveLength(1)
    expect(holdingsApi.addStockLot).not.toHaveBeenCalled()
  })

  describe('editing an entry', () => {
    const lot: PositionEntry = {
      id: 'lot-1',
      date: '2026-01-12',
      quantity: 300,
      price: 25.1,
      amount: 7530,
    }
    const contribution: PositionEntry = {
      id: 'c-1',
      date: '2026-01-10',
      quantity: null,
      price: null,
      amount: 4000,
    }

    function mountEditModal(kind: WalletKind, entry: PositionEntry, holdingLabel?: string) {
      activeWrapper = mount(AddPositionModal, {
        props: {
          holdingId: 'holding-1',
          walletId: 'wallet-1',
          kind,
          walletCurrency: 'BRL',
          entry,
          holdingLabel,
        },
        global: { config: { errorHandler: () => undefined } },
        attachTo: document.body,
      })
      return activeWrapper
    }

    function rejectionOf(detail: string) {
      return Object.assign(new Error('400'), {
        isAxiosError: true,
        response: { status: 400, data: { detail } },
      })
    }

    it('titles a lot edit and prefills its values', () => {
      const wrapper = mountEditModal('STOCKS', lot, 'PETR4 · Carteira B3')

      expect(document.body.textContent).toContain('Editar compra')
      expect(document.body.textContent).toContain('PETR4 · Carteira B3 · registrada em 12 jan 2026')
      const [quantityInput, priceInput] = wrapper.findAll<HTMLInputElement>('input[type="number"]')
      expect(Number(quantityInput.element.value)).toBe(300)
      expect(Number(priceInput.element.value)).toBe(25.1)
      expect(wrapper.get('[data-testid="position-total"]').text()).toContain('R$ 7.530,00')
    })

    it('titles a contribution edit, prefills the amount and names the date without a label', () => {
      const wrapper = mountEditModal('FUNDS', contribution)

      expect(document.body.textContent).toContain('Editar aporte')
      expect(document.body.textContent).toContain('registrada em 10 jan 2026')
      expect(Number(wrapper.get<HTMLInputElement>('input[type="number"]').element.value)).toBe(4000)
      expect(wrapper.get('[data-testid="position-total"]').text()).toContain('Total aportado')
    })

    it('keeps Salvar alterações disabled until a value changes, then shows the previous total', async () => {
      const wrapper = mountEditModal('STOCKS', lot)

      expect(submitButton(wrapper, 'Salvar alterações').attributes('disabled')).toBeDefined()
      expect(wrapper.find('[data-testid="position-impact"]').exists()).toBe(false)

      await wrapper.findAll('input[type="number"]')[1].setValue('2.51')

      expect(submitButton(wrapper, 'Salvar alterações').attributes('disabled')).toBeUndefined()
      expect(wrapper.get('[data-testid="position-impact"]').text()).toBe(
        'Antes: R$ 7.530,00. Preço médio e resultado serão recalculados.',
      )
      expect(wrapper.get('[data-testid="position-total"]').text()).toContain('R$ 753,00')
    })

    it('keeps saving disabled while the date is cleared', async () => {
      const wrapper = mountEditModal('STOCKS', lot)
      await wrapper.findAll('input[type="number"]')[0].setValue('30')

      wrapper.getComponent({ name: 'BDatepicker' }).vm.$emit('update:modelValue', null)
      await flushPromises()

      expect(submitButton(wrapper, 'Salvar alterações').attributes('disabled')).toBeDefined()
      expect(wrapper.find('[data-testid="position-impact"]').exists()).toBe(false)
    })

    it('treats a new date as a change', async () => {
      const wrapper = mountEditModal('STOCKS', lot)

      wrapper
        .getComponent({ name: 'BDatepicker' })
        .vm.$emit('update:modelValue', new Date('2026-02-03T12:00:00Z'))
      await flushPromises()

      expect(submitButton(wrapper, 'Salvar alterações').attributes('disabled')).toBeUndefined()
    })

    it.each([
      ['STOCKS', 'updateStockLot'],
      ['CRYPTO', 'updateCryptoLot'],
    ] as const)('saves a %s lot through %s', async (kind, method) => {
      vi.mocked(holdingsApi[method]).mockResolvedValue({} as never)
      const wrapper = mountEditModal(kind, lot)

      await wrapper.findAll('input[type="number"]')[0].setValue('30')
      await submitButton(wrapper, 'Salvar alterações').trigger('click')
      await flushPromises()

      expect(holdingsApi[method]).toHaveBeenCalledWith('wallet-1', 'holding-1', 'lot-1', {
        lotDate: '2026-01-12',
        quantity: 30,
        price: 25.1,
      })
      expect(document.body.textContent).toContain('Compra atualizada!')
      expect(wrapper.emitted('updated')).toHaveLength(1)
      expect(wrapper.emitted('added')).toBeUndefined()
      expect(wrapper.emitted('close')).toHaveLength(1)
    })

    it('saves a contribution with its amount and words the impact for a fund', async () => {
      vi.mocked(holdingsApi.updateFundContribution).mockResolvedValue({} as never)
      const wrapper = mountEditModal('FUNDS', contribution)

      await wrapper.find('input[type="number"]').setValue('4500')
      expect(wrapper.get('[data-testid="position-impact"]').text()).toBe(
        'Antes: R$ 4.000,00. O valor investido e o resultado serão recalculados.',
      )
      await submitButton(wrapper, 'Salvar alterações').trigger('click')
      await flushPromises()

      expect(holdingsApi.updateFundContribution).toHaveBeenCalledWith(
        'wallet-1',
        'holding-1',
        'c-1',
        { contributionDate: '2026-01-10', amount: 4500 },
      )
      expect(document.body.textContent).toContain('Aporte atualizado!')
      expect(wrapper.emitted('updated')).toHaveLength(1)
    })

    it('shows a rejected correction inline and stays open', async () => {
      vi.mocked(holdingsApi.updateStockLot).mockRejectedValue(
        rejectionOf('A quantidade das compras não pode ficar abaixo da quantidade já vendida'),
      )
      const wrapper = mountEditModal('STOCKS', lot)

      await wrapper.findAll('input[type="number"]')[0].setValue('10')
      await submitButton(wrapper, 'Salvar alterações').trigger('click')
      await flushPromises()

      expect(wrapper.get('[data-testid="position-error"]').text()).toBe(
        'A quantidade das compras não pode ficar abaixo da quantidade já vendida',
      )
      expect(wrapper.emitted('updated')).toBeUndefined()
      expect(wrapper.emitted('close')).toBeUndefined()
    })

    it('clears the previous message when it is submitted again', async () => {
      vi.mocked(holdingsApi.updateStockLot)
        .mockRejectedValueOnce(rejectionOf('Quantidade inválida'))
        .mockResolvedValueOnce({} as never)
      const wrapper = mountEditModal('STOCKS', lot)

      await wrapper.findAll('input[type="number"]')[0].setValue('10')
      await submitButton(wrapper, 'Salvar alterações').trigger('click')
      await flushPromises()
      expect(wrapper.find('[data-testid="position-error"]').exists()).toBe(true)

      await submitButton(wrapper, 'Salvar alterações').trigger('click')
      await flushPromises()

      expect(wrapper.find('[data-testid="position-error"]').exists()).toBe(false)
      expect(wrapper.emitted('updated')).toHaveLength(1)
    })
  })
})
