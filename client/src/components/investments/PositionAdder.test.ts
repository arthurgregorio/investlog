import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import PositionAdder from './PositionAdder.vue'
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

function mountAdder(kind: WalletKind = 'STOCKS', walletCurrency = 'BRL') {
  activeWrapper = mount(PositionAdder, {
    props: { holdingId: 'holding-1', walletId: 'wallet-1', kind, walletCurrency },
    global: { config: { errorHandler: () => undefined } },
    attachTo: document.body,
  })
  return activeWrapper
}

function addButton(wrapper: VueWrapper) {
  return wrapper.findAll('button').find((button) => button.text() === 'Adicionar')!
}

function cancelButton(wrapper: VueWrapper) {
  return wrapper.findAll('button').find((button) => button.text() === 'Cancelar')!
}

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

describe('PositionAdder', () => {
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
    'asks for quantity and price on a %s holding, with no amount field',
    (kind) => {
      const wrapper = mountAdder(kind)

      expect(wrapper.text()).toContain('Quantidade')
      expect(wrapper.text()).toContain('Preço')
      expect(wrapper.text()).not.toContain('Valor aportado')
      expect(wrapper.findAll('input[type="number"]')).toHaveLength(2)
    },
  )

  it('asks only for the amount on a fund holding', () => {
    const wrapper = mountAdder('FUNDS')

    expect(wrapper.text()).toContain('Valor aportado')
    expect(wrapper.text()).not.toContain('Quantidade')
    expect(wrapper.findAll('input[type="number"]')).toHaveLength(1)
  })

  it('prefixes the money fields with the symbol of the wallet currency', () => {
    const wrapper = mountAdder('STOCKS', 'USD')

    expect(wrapper.findAll('.button.is-static').map((prefix) => prefix.text())).toEqual(['US$'])
  })

  it('keeps Adicionar disabled until quantity and price are both positive', async () => {
    const wrapper = mountAdder('STOCKS')
    const [quantityInput, priceInput] = wrapper.findAll('input[type="number"]')

    expect(addButton(wrapper).attributes('disabled')).toBeDefined()

    await quantityInput.setValue('10')
    expect(addButton(wrapper).attributes('disabled')).toBeDefined()

    await priceInput.setValue('0')
    expect(addButton(wrapper).attributes('disabled')).toBeDefined()

    await priceInput.setValue('36.5')
    expect(addButton(wrapper).attributes('disabled')).toBeUndefined()

    await quantityInput.setValue('-1')
    expect(addButton(wrapper).attributes('disabled')).toBeDefined()
  })

  it('keeps Adicionar disabled on a fund until the amount is positive', async () => {
    const wrapper = mountAdder('FUNDS')
    const amountInput = wrapper.find('input[type="number"]')

    expect(addButton(wrapper).attributes('disabled')).toBeDefined()

    await amountInput.setValue('0')
    expect(addButton(wrapper).attributes('disabled')).toBeDefined()

    await amountInput.setValue('250')
    expect(addButton(wrapper).attributes('disabled')).toBeUndefined()
  })

  it('registers a stock purchase as a lot and announces it without closing', async () => {
    vi.mocked(holdingsApi.addStockLot).mockResolvedValue({} as never)
    const wrapper = mountAdder('STOCKS')
    const [quantityInput, priceInput] = wrapper.findAll('input[type="number"]')

    await quantityInput.setValue('10')
    await priceInput.setValue('36.5')
    await addButton(wrapper).trigger('click')
    await flushPromises()

    expect(holdingsApi.addStockLot).toHaveBeenCalledWith('wallet-1', 'holding-1', {
      lotDate: '2026-03-15',
      quantity: 10,
      price: 36.5,
    })
    expect(holdingsApi.addCryptoLot).not.toHaveBeenCalled()
    expect(wrapper.emitted('added')).toHaveLength(1)
    expect(wrapper.emitted('close')).toBeUndefined()
  })

  it('routes a crypto purchase to the crypto lot endpoint', async () => {
    vi.mocked(holdingsApi.addCryptoLot).mockResolvedValue({} as never)
    const wrapper = mountAdder('CRYPTO')
    const [quantityInput, priceInput] = wrapper.findAll('input[type="number"]')

    await quantityInput.setValue('0.25')
    await priceInput.setValue('300000')
    await addButton(wrapper).trigger('click')
    await flushPromises()

    expect(holdingsApi.addCryptoLot).toHaveBeenCalledWith('wallet-1', 'holding-1', {
      lotDate: '2026-03-15',
      quantity: 0.25,
      price: 300000,
    })
    expect(holdingsApi.addStockLot).not.toHaveBeenCalled()
    expect(wrapper.emitted('added')).toHaveLength(1)
  })

  it('registers a fund contribution with the amount', async () => {
    vi.mocked(holdingsApi.addContribution).mockResolvedValue({} as never)
    const wrapper = mountAdder('FUNDS')

    await wrapper.find('input[type="number"]').setValue('500')
    await addButton(wrapper).trigger('click')
    await flushPromises()

    expect(holdingsApi.addContribution).toHaveBeenCalledWith('wallet-1', 'holding-1', {
      contributionDate: '2026-03-15',
      amount: 500,
    })
    expect(wrapper.emitted('added')).toHaveLength(1)
  })

  it('does not announce anything and lets the user retry when the request is rejected', async () => {
    vi.mocked(holdingsApi.addStockLot).mockRejectedValueOnce(new Error('network'))
    const wrapper = mountAdder('STOCKS')
    const [quantityInput, priceInput] = wrapper.findAll('input[type="number"]')

    await quantityInput.setValue('10')
    await priceInput.setValue('36.5')
    await addButton(wrapper).trigger('click')
    await flushPromises()

    expect(wrapper.emitted('added')).toBeUndefined()
    expect(wrapper.emitted('close')).toBeUndefined()
    expect(addButton(wrapper).attributes('disabled')).toBeUndefined()
    expect(addButton(wrapper).classes()).not.toContain('is-loading')

    vi.mocked(holdingsApi.addStockLot).mockResolvedValueOnce({} as never)
    await addButton(wrapper).trigger('click')
    await flushPromises()

    expect(holdingsApi.addStockLot).toHaveBeenCalledTimes(2)
    expect(wrapper.emitted('added')).toHaveLength(1)
  })

  it('does not call the API when submitted while invalid', async () => {
    const wrapper = mountAdder('STOCKS')

    await addButton(wrapper).trigger('click')
    await flushPromises()

    expect(holdingsApi.addStockLot).not.toHaveBeenCalled()
    expect(wrapper.emitted('added')).toBeUndefined()
  })

  it('emits close without saving when cancelled', async () => {
    const wrapper = mountAdder('STOCKS')

    await cancelButton(wrapper).trigger('click')

    expect(wrapper.emitted('close')).toHaveLength(1)
    expect(holdingsApi.addStockLot).not.toHaveBeenCalled()
  })
})
