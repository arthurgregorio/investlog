import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { useAddInvestmentForm } from './useAddInvestmentForm'
import { useWalletsStore } from '@/stores/wallets'
import { useTypesListStore } from '@/stores/typesList'
import * as holdingsApiModule from '@/api/holdings'
import type { AssetType, WalletResponse } from '@/types'

vi.mock('@/api/holdings')
vi.mock('buefy', () => ({
  useToast: () => ({ open: vi.fn() }),
  ToastProgrammatic: class {
    open = vi.fn()
  },
}))

const mockWallet: WalletResponse = {
  id: 'wallet-stocks-1',
  name: 'Carteira Ações',
  kind: 'STOCKS',
  currency: 'BRL',
  holdingCount: 0,
  totalInvested: 0,
  currentValue: null,
  gain: null,
  gainPct: null,
  createdAt: '2024-01-01T00:00:00Z',
}

const mockFundWallet: WalletResponse = {
  id: 'wallet-funds-1',
  name: 'Carteira Fundos',
  kind: 'FUNDS',
  currency: 'BRL',
  holdingCount: 0,
  totalInvested: 0,
  currentValue: null,
  gain: null,
  gainPct: null,
  createdAt: '2024-01-01T00:00:00Z',
}

const mockStockType: AssetType = { id: 'type-1', name: 'Ação Ordinária', usageCount: 0 }
const mockFundType: AssetType = { id: 'type-fund-1', name: 'Renda Fixa', usageCount: 0 }

describe('useAddInvestmentForm', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()

    const walletsStore = useWalletsStore()
    walletsStore.wallets = [mockWallet, mockFundWallet]
    walletsStore.loaded = true

    const typesListStore = useTypesListStore()
    typesListStore.stockTypes = [mockStockType]
    typesListStore.fundTypes = [mockFundType]
    typesListStore.loaded = true
  })

  it('starts with kind=STOCKS and selects first wallet', () => {
    const { form } = useAddInvestmentForm('STOCKS')
    expect(form.kind).toBe('STOCKS')
    expect(form.walletId).toBe('wallet-stocks-1')
  })

  it('valid=false when ticker is empty', () => {
    const { form } = useAddInvestmentForm('STOCKS')
    form.ticker = ''
    form.quantity = 10
    form.price = 50
    form.date = new Date()
    expect(form.valid).toBe(false)
  })

  it('valid=true when all required stock fields are filled', () => {
    const { form } = useAddInvestmentForm('STOCKS')
    form.ticker = 'PETR4'
    form.quantity = 10
    form.price = 50
    form.date = new Date()
    expect(form.valid).toBe(true)
  })

  it('valid=false when stock quantity is 0', () => {
    const { form } = useAddInvestmentForm('STOCKS')
    form.ticker = 'PETR4'
    form.quantity = 0
    form.price = 50
    form.date = new Date()
    expect(form.valid).toBe(false)
  })

  it('switches walletsOfKind when kind changes to funds', () => {
    const { form } = useAddInvestmentForm('STOCKS')
    expect(form.walletsOfKind.map((wallet) => wallet.id)).toEqual(['wallet-stocks-1'])

    form.kind = 'FUNDS'
    expect(form.walletsOfKind.map((wallet) => wallet.id)).toEqual(['wallet-funds-1'])
  })

  it('valid=false for funds when name is empty', () => {
    const { form } = useAddInvestmentForm('FUNDS')
    form.walletId = 'wallet-funds-1'
    form.name = ''
    form.amount = 500
    form.date = new Date()
    expect(form.valid).toBe(false)
  })

  it('valid=true for funds when all required fields are filled', () => {
    const { form } = useAddInvestmentForm('FUNDS')
    form.walletId = 'wallet-funds-1'
    form.fundTypeId = 'type-fund-1'
    form.name = 'Tesouro Direto'
    form.amount = 500
    form.date = new Date()
    expect(form.valid).toBe(true)
  })

  it('submit calls createCryptoHolding with an upper-cased ticker and the current price', async () => {
    const walletsStore = useWalletsStore()
    walletsStore.wallets = [
      ...walletsStore.wallets,
      { ...mockWallet, id: 'wallet-crypto-1', kind: 'CRYPTO' },
    ]
    const onDone = vi.fn()

    const { form, submit } = useAddInvestmentForm('CRYPTO', onDone)
    form.ticker = ' btc '
    form.quantity = 0.5
    form.price = 300000
    form.currentPrice = 310000
    form.date = new Date('2024-06-01')

    await submit()

    expect(holdingsApiModule.holdingsApi.createCryptoHolding).toHaveBeenCalledWith(
      'wallet-crypto-1',
      {
        ticker: 'BTC',
        name: undefined,
        currentPrice: 310000,
        lot: { lotDate: '2024-06-01', quantity: 0.5, price: 300000 },
      },
    )
    expect(onDone).toHaveBeenCalledWith('CRYPTO')
    expect(form.submitting).toBe(false)
  })

  it('submit calls createFundHolding with the contribution and the optional current value', async () => {
    const onDone = vi.fn()

    const { form, submit } = useAddInvestmentForm('FUNDS', onDone)
    form.name = '  Tesouro Direto  '
    form.amount = 500
    form.currentValue = 520
    form.date = new Date('2024-06-01')

    await submit()

    expect(holdingsApiModule.holdingsApi.createFundHolding).toHaveBeenCalledWith(
      'wallet-funds-1',
      {
        fundTypeId: 'type-fund-1',
        name: 'Tesouro Direto',
        currentValue: 520,
        contribution: { contributionDate: '2024-06-01', amount: 500 },
      },
    )
    expect(onDone).toHaveBeenCalledWith('FUNDS')
  })

  it('submit omits the current value of a fund when it is blank', async () => {
    const { form, submit } = useAddInvestmentForm('FUNDS')
    form.name = 'Tesouro Direto'
    form.amount = 500
    form.date = new Date('2024-06-01')

    await submit()

    expect(holdingsApiModule.holdingsApi.createFundHolding).toHaveBeenCalledWith(
      'wallet-funds-1',
      expect.objectContaining({ currentValue: undefined }),
    )
  })

  describe('fund fee rates', () => {
    function fillRequiredFundFields(form: ReturnType<typeof useAddInvestmentForm>['form']) {
      form.name = 'Tesouro Direto'
      form.amount = 500
      form.date = new Date('2024-06-01')
    }

    function createFundPayload() {
      return vi.mocked(holdingsApiModule.holdingsApi.createFundHolding).mock.calls[0][1]
    }

    it('starts with both rates blank', () => {
      const { form } = useAddInvestmentForm('FUNDS')

      expect(form.administrationFeeRate).toBe('')
      expect(form.performanceFeeRate).toBe('')
    })

    it('stays valid and submittable with both rates blank', async () => {
      const { form, submit } = useAddInvestmentForm('FUNDS')
      fillRequiredFundFields(form)

      expect(form.valid).toBe(true)
      await submit()

      expect(holdingsApiModule.holdingsApi.createFundHolding).toHaveBeenCalledTimes(1)
    })

    it('sends both rates on the create call', async () => {
      const { form, submit } = useAddInvestmentForm('FUNDS')
      fillRequiredFundFields(form)
      form.administrationFeeRate = 1.5
      form.performanceFeeRate = 20

      await submit()

      expect(holdingsApiModule.holdingsApi.createFundHolding).toHaveBeenCalledWith(
        'wallet-funds-1',
        {
          fundTypeId: 'type-fund-1',
          name: 'Tesouro Direto',
          currentValue: undefined,
          administrationFeeRate: 1.5,
          performanceFeeRate: 20,
          contribution: { contributionDate: '2024-06-01', amount: 500 },
        },
      )
    })

    it('leaves both rates out of the payload when they are blank', async () => {
      const { form, submit } = useAddInvestmentForm('FUNDS')
      fillRequiredFundFields(form)

      await submit()

      expect(createFundPayload()).not.toHaveProperty('administrationFeeRate')
      expect(createFundPayload()).not.toHaveProperty('performanceFeeRate')
    })

    it('sends only the administration rate when the performance rate is blank', async () => {
      const { form, submit } = useAddInvestmentForm('FUNDS')
      fillRequiredFundFields(form)
      form.administrationFeeRate = 0.8

      await submit()

      expect(createFundPayload()).toHaveProperty('administrationFeeRate', 0.8)
      expect(createFundPayload()).not.toHaveProperty('performanceFeeRate')
    })

    it('sends only the performance rate when the administration rate is blank', async () => {
      const { form, submit } = useAddInvestmentForm('FUNDS')
      fillRequiredFundFields(form)
      form.performanceFeeRate = 15

      await submit()

      expect(createFundPayload()).not.toHaveProperty('administrationFeeRate')
      expect(createFundPayload()).toHaveProperty('performanceFeeRate', 15)
    })

    it('sends a rate of zero instead of dropping it', async () => {
      const { form, submit } = useAddInvestmentForm('FUNDS')
      fillRequiredFundFields(form)
      form.administrationFeeRate = 0
      form.performanceFeeRate = 0

      await submit()

      expect(createFundPayload()).toHaveProperty('administrationFeeRate', 0)
      expect(createFundPayload()).toHaveProperty('performanceFeeRate', 0)
    })

    it('does not send the rates for stock and crypto holdings', async () => {
      const { form, submit } = useAddInvestmentForm('STOCKS')
      form.ticker = 'PETR4'
      form.quantity = 10
      form.price = 36.5
      form.date = new Date('2024-06-01')
      form.administrationFeeRate = 1.5

      await submit()

      const stockPayload = vi.mocked(holdingsApiModule.holdingsApi.createStockHolding).mock
        .calls[0][1]
      expect(stockPayload).not.toHaveProperty('administrationFeeRate')
      expect(stockPayload).not.toHaveProperty('performanceFeeRate')
    })
  })

  it('submit does nothing while the form is invalid', async () => {
    const onDone = vi.fn()

    const { form, submit } = useAddInvestmentForm('STOCKS', onDone)
    form.ticker = ''

    await submit()

    expect(holdingsApiModule.holdingsApi.createStockHolding).not.toHaveBeenCalled()
    expect(onDone).not.toHaveBeenCalled()
    expect(form.submitting).toBe(false)
  })

  it('submit leaves submitting back at false and skips onDone when the request is rejected', async () => {
    vi.mocked(holdingsApiModule.holdingsApi.createStockHolding).mockRejectedValue(
      new Error('network'),
    )
    const onDone = vi.fn()

    const { form, submit } = useAddInvestmentForm('STOCKS', onDone)
    form.ticker = 'PETR4'
    form.quantity = 10
    form.price = 36.5
    form.date = new Date('2024-06-01')

    await expect(submit()).rejects.toThrow('network')

    expect(form.submitting).toBe(false)
    expect(onDone).not.toHaveBeenCalled()
  })

  it('ignores a second submit while the first is still in flight', async () => {
    let finishRequest: () => void = () => undefined
    vi.mocked(holdingsApiModule.holdingsApi.createStockHolding).mockReturnValue(
      new Promise((resolve) => {
        finishRequest = () => resolve({} as never)
      }),
    )

    const { form, submit } = useAddInvestmentForm('STOCKS')
    form.ticker = 'PETR4'
    form.quantity = 10
    form.price = 36.5
    form.date = new Date('2024-06-01')

    const firstSubmit = submit()
    await submit()
    finishRequest()
    await firstSubmit

    expect(holdingsApiModule.holdingsApi.createStockHolding).toHaveBeenCalledTimes(1)
  })

  it('selects the first wallet of the new kind when the kind changes', async () => {
    const { form } = useAddInvestmentForm('STOCKS')

    form.kind = 'FUNDS'
    await nextTick()

    expect(form.walletId).toBe('wallet-funds-1')
  })

  it('moves to another wallet when the selected one disappears from the list', async () => {
    const walletsStore = useWalletsStore()
    walletsStore.wallets = [
      mockWallet,
      { ...mockWallet, id: 'wallet-stocks-2', name: 'Outra Carteira' },
    ]
    const { form } = useAddInvestmentForm('STOCKS')
    form.walletId = 'wallet-stocks-2'

    walletsStore.wallets = [mockWallet]
    await nextTick()

    expect(form.walletId).toBe('wallet-stocks-1')
  })

  it('reselects a type when the selected stock or fund type is removed', async () => {
    const typesListStore = useTypesListStore()
    const { form } = useAddInvestmentForm('STOCKS')
    expect(form.stockTypeId).toBe('type-1')
    expect(form.fundTypeId).toBe('type-fund-1')

    typesListStore.stockTypes = [{ id: 'type-2', name: 'FII', usageCount: 0 }]
    typesListStore.fundTypes = []
    await nextTick()

    expect(form.stockTypeId).toBe('type-2')
    expect(form.fundTypeId).toBe('')
  })

  it('submit calls createStockHolding with correct payload', async () => {
    vi.mocked(holdingsApiModule.holdingsApi.createStockHolding).mockResolvedValue({
      id: 'new-holding-id',
      walletId: 'wallet-stocks-1',
      stockTypeId: 'type-1',
      ticker: 'PETR4',
      name: '',
      currentPrice: null,
      lots: [],
      withdrawals: [],
    })

    const { form, submit } = useAddInvestmentForm('STOCKS')
    form.ticker = 'PETR4'
    form.quantity = 10
    form.price = 36.5
    form.date = new Date('2024-06-01')
    form.stockTypeId = 'type-1'

    await submit()

    expect(holdingsApiModule.holdingsApi.createStockHolding).toHaveBeenCalledWith(
      'wallet-stocks-1',
      expect.objectContaining({
        ticker: 'PETR4',
        stockTypeId: 'type-1',
        lot: expect.objectContaining({
          quantity: 10,
          price: 36.5,
          lotDate: '2024-06-01',
        }),
      }),
    )
  })
})
