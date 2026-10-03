import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import AddPositionModal from './AddPositionModal.vue'
import { holdingsApi } from '@/api/holdings'
import type { WalletKind } from '@/types'

vi.mock('@/api/holdings', () => ({
  holdingsApi: {
    addStockLot: vi.fn(),
    addCryptoLot: vi.fn(),
    addContribution: vi.fn(),
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
})
