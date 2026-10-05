import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import AddInvestmentModal from './AddInvestmentModal.vue'
import { holdingsApi } from '@/api/holdings'
import { useWalletsStore } from '@/stores/wallets'
import { useTypesListStore } from '@/stores/typesList'
import { useHoldingsListStore } from '@/stores/holdingsList'
import type { WalletKind, WalletResponse } from '@/types'

vi.mock('@/api/holdings', () => ({
  holdingsApi: {
    createStockHolding: vi.fn(),
    createCryptoHolding: vi.fn(),
    createFundHolding: vi.fn(),
  },
}))

let activeWrapper: VueWrapper | undefined

function wallet(id: string, kind: WalletKind): WalletResponse {
  return {
    id,
    name: `Carteira ${id}`,
    kind,
    currency: 'BRL',
    holdingCount: 0,
    totalInvested: 0,
    currentValue: null,
    gain: null,
    gainPct: null,
    createdAt: '2026-01-01T00:00:00Z',
  }
}

function mountModal(options: { initialKind?: WalletKind; wallets?: WalletResponse[] } = {}) {
  const pinia = createTestingPinia({
    initialState: {
      wallets: { wallets: options.wallets ?? [wallet('stocks-1', 'STOCKS')] },
      typesList: {
        stockTypes: [{ id: 'stock-type-1', name: 'Ação', usageCount: 0 }],
        fundTypes: [{ id: 'fund-type-1', name: 'Renda Fixa', usageCount: 0 }],
      },
    },
  })
  activeWrapper = mount(AddInvestmentModal, {
    props: { initialKind: options.initialKind },
    global: { plugins: [pinia] },
    attachTo: document.body,
  })
  return activeWrapper
}

function buttonLabelled(wrapper: VueWrapper, label: string) {
  return wrapper.findAll('button').find((button) => button.text().includes(label))!
}

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

describe('AddInvestmentModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    activeWrapper?.unmount()
    activeWrapper = undefined
  })

  it('loads the wallets and the asset types when it opens', () => {
    mountModal()

    expect(useWalletsStore().load).toHaveBeenCalled()
    expect(useTypesListStore().load).toHaveBeenCalled()
  })

  it('keeps Adicionar disabled until the form is valid', async () => {
    const wrapper = mountModal()

    expect(buttonLabelled(wrapper, 'Adicionar').attributes('disabled')).toBeDefined()

    await wrapper.find('input[placeholder="PETR4"]').setValue('petr4')
    const [quantityInput, priceInput] = wrapper.findAll('input[type="number"]')
    await quantityInput.setValue('10')
    await priceInput.setValue('36.5')

    expect(buttonLabelled(wrapper, 'Adicionar').attributes('disabled')).toBeUndefined()
  })

  it('submits a stock, refreshes the holdings list and closes', async () => {
    vi.mocked(holdingsApi.createStockHolding).mockResolvedValue({} as never)
    const wrapper = mountModal()

    await wrapper.find('input[placeholder="PETR4"]').setValue('petr4')
    const [quantityInput, priceInput] = wrapper.findAll('input[type="number"]')
    await quantityInput.setValue('10')
    await priceInput.setValue('36.5')
    await buttonLabelled(wrapper, 'Adicionar').trigger('click')
    await flushPromises()

    expect(holdingsApi.createStockHolding).toHaveBeenCalledWith(
      'stocks-1',
      expect.objectContaining({
        stockTypeId: 'stock-type-1',
        ticker: 'PETR4',
        lot: expect.objectContaining({ quantity: 10, price: 36.5 }),
      }),
    )
    expect(useHoldingsListStore().refresh).toHaveBeenCalled()
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('submits a fund contribution when opened on the funds kind', async () => {
    vi.mocked(holdingsApi.createFundHolding).mockResolvedValue({} as never)
    const wrapper = mountModal({
      initialKind: 'FUNDS',
      wallets: [wallet('funds-1', 'FUNDS')],
    })

    await wrapper.find('input[placeholder="ex.: Tesouro Selic 2029"]').setValue('Tesouro Selic')
    await wrapper.findAll('input[type="number"]')[2].setValue('1500')
    await buttonLabelled(wrapper, 'Adicionar').trigger('click')
    await flushPromises()

    expect(holdingsApi.createFundHolding).toHaveBeenCalledWith(
      'funds-1',
      expect.objectContaining({
        fundTypeId: 'fund-type-1',
        name: 'Tesouro Selic',
        contribution: expect.objectContaining({ amount: 1500 }),
      }),
    )
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('forwards the create-wallet request of the form when no wallet fits the kind', async () => {
    const wrapper = mountModal({ initialKind: 'CRYPTO' })

    expect(document.body.textContent).toContain('Nenhuma carteira de cripto ainda.')
    await buttonLabelled(wrapper, 'Criar carteira').trigger('click')

    expect(wrapper.emitted('create-wallet')).toEqual([['CRYPTO']])
  })

  it('closes without submitting when cancelled', async () => {
    const wrapper = mountModal()

    await buttonLabelled(wrapper, 'Cancelar').trigger('click')

    expect(wrapper.emitted('close')).toHaveLength(1)
    expect(holdingsApi.createStockHolding).not.toHaveBeenCalled()
    expect(useHoldingsListStore().refresh).not.toHaveBeenCalled()
  })
})
