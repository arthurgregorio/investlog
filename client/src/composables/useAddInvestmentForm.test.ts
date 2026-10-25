import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { useAddInvestmentForm } from './useAddInvestmentForm'
import { useWalletsStore } from '@/stores/wallets'
import { useTypesListStore } from '@/stores/typesList'
import * as holdingsApiModule from '@/api/holdings'
import type { HoldingType, WalletResponse } from '@/types'

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

const mockStockType: HoldingType = {
  id: 'type-1',
  name: 'Ação Ordinária',
  kinds: ['STOCKS'],
  usageCount: 0,
}
const mockFundType: HoldingType = {
  id: 'type-fund-1',
  name: 'Renda Fixa',
  kinds: ['FUNDS'],
  usageCount: 0,
}
const mockCryptoType: HoldingType = {
  id: 'type-crypto-1',
  name: 'Moeda',
  kinds: ['CRYPTO'],
  usageCount: 0,
}

describe('useAddInvestmentForm', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()

    const walletsStore = useWalletsStore()
    walletsStore.wallets = [mockWallet, mockFundWallet]
    walletsStore.loaded = true

    const typesListStore = useTypesListStore()
    typesListStore.holdingTypes = [mockStockType, mockFundType]
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

  describe('initial wallet', () => {
    const secondStockWallet: WalletResponse = {
      ...mockWallet,
      id: 'wallet-stocks-2',
      name: 'Carteira Ações 2',
    }

    beforeEach(() => {
      useWalletsStore().wallets = [mockWallet, secondStockWallet, mockFundWallet]
    })

    it('starts on the given wallet when it belongs to the selected kind', () => {
      const { form } = useAddInvestmentForm('STOCKS', 'wallet-stocks-2')

      expect(form.walletId).toBe('wallet-stocks-2')
    })

    it('falls back to the first wallet of the kind when the given wallet is of another kind', () => {
      const { form } = useAddInvestmentForm('STOCKS', 'wallet-funds-1')

      expect(form.walletId).toBe('wallet-stocks-1')
    })

    it('selects the given wallet once a late wallets load brings it in', async () => {
      const walletsStore = useWalletsStore()
      walletsStore.wallets = []
      const { form } = useAddInvestmentForm('STOCKS', 'wallet-stocks-2')
      expect(form.walletId).toBe('')

      walletsStore.wallets = [mockWallet, secondStockWallet, mockFundWallet]
      await nextTick()

      expect(form.walletId).toBe('wallet-stocks-2')
    })

    it('still moves to the first wallet of a newly chosen kind', async () => {
      const { form } = useAddInvestmentForm('STOCKS', 'wallet-stocks-2')

      form.kind = 'FUNDS'
      await nextTick()
      expect(form.walletId).toBe('wallet-funds-1')

      form.kind = 'STOCKS'
      await nextTick()
      expect(form.walletId).toBe('wallet-stocks-1')
    })
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
    form.typeId = 'type-fund-1'
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

    const { form, submit } = useAddInvestmentForm('CRYPTO', undefined, onDone)
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

    const { form, submit } = useAddInvestmentForm('FUNDS', undefined, onDone)
    form.name = '  Tesouro Direto  '
    form.amount = 500
    form.currentValue = 520
    form.date = new Date('2024-06-01')

    await submit()

    expect(holdingsApiModule.holdingsApi.createFundHolding).toHaveBeenCalledWith('wallet-funds-1', {
      typeId: 'type-fund-1',
      name: 'Tesouro Direto',
      currentValue: 520,
      contribution: { contributionDate: '2024-06-01', amount: 500 },
    })
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
          typeId: 'type-fund-1',
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

    const { form, submit } = useAddInvestmentForm('STOCKS', undefined, onDone)
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

    const { form, submit } = useAddInvestmentForm('STOCKS', undefined, onDone)
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

  describe('type of the selected kind', () => {
    it('preselects the first type that applies to the kind', () => {
      expect(useAddInvestmentForm('STOCKS').form.typeId).toBe('type-1')
      expect(useAddInvestmentForm('FUNDS').form.typeId).toBe('type-fund-1')
    })

    it('reselects a type when the selected one is removed from the list', async () => {
      const typesListStore = useTypesListStore()
      const { form } = useAddInvestmentForm('STOCKS')
      expect(form.typeId).toBe('type-1')

      typesListStore.holdingTypes = [
        { id: 'type-2', name: 'FII', kinds: ['STOCKS'], usageCount: 0 },
        mockFundType,
      ]
      await nextTick()

      expect(form.typeId).toBe('type-2')
    })

    it('clears the type when no type applies to the kind any more', async () => {
      const typesListStore = useTypesListStore()
      const { form } = useAddInvestmentForm('FUNDS')

      typesListStore.holdingTypes = [mockStockType]
      await nextTick()

      expect(form.typeId).toBe('')
    })

    it('swaps to a type of the new kind when the kind changes', async () => {
      const { form } = useAddInvestmentForm('STOCKS')

      form.kind = 'FUNDS'
      await nextTick()

      expect(form.typeId).toBe('type-fund-1')
    })

    it('a type shared by two kinds stays selected across the switch', async () => {
      const sharedType: HoldingType = {
        id: 'type-shared',
        name: 'Renda Variável',
        kinds: ['STOCKS', 'FUNDS'],
        usageCount: 0,
      }
      useTypesListStore().holdingTypes = [sharedType, mockStockType, mockFundType]
      const { form } = useAddInvestmentForm('STOCKS')
      expect(form.typeId).toBe('type-shared')

      form.kind = 'FUNDS'
      await nextTick()

      expect(form.typeId).toBe('type-shared')
    })

    it('is required for stocks', () => {
      useTypesListStore().holdingTypes = []
      const { form } = useAddInvestmentForm('STOCKS')
      form.ticker = 'PETR4'
      form.quantity = 10
      form.price = 50
      form.date = new Date()

      expect(form.valid).toBe(false)
    })

    describe('for crypto', () => {
      beforeEach(() => {
        useWalletsStore().wallets = [
          mockWallet,
          { ...mockWallet, id: 'wallet-crypto-1', kind: 'CRYPTO' },
        ]
      })

      function fillCryptoPurchase(form: ReturnType<typeof useAddInvestmentForm>['form']) {
        form.ticker = 'BTC'
        form.quantity = 1
        form.price = 300000
        form.date = new Date('2024-06-01')
      }

      it('is optional: no type is selected and the form is still valid', () => {
        const { form } = useAddInvestmentForm('CRYPTO')
        fillCryptoPurchase(form)

        expect(form.typeId).toBe('')
        expect(form.valid).toBe(true)
      })

      it('is preselected when exactly one crypto type exists', () => {
        useTypesListStore().holdingTypes = [mockStockType, mockCryptoType]

        const { form } = useAddInvestmentForm('CRYPTO')

        expect(form.typeId).toBe('type-crypto-1')
      })

      it('is left unselected when several crypto types exist', () => {
        useTypesListStore().holdingTypes = [
          mockCryptoType,
          { ...mockCryptoType, id: 'type-crypto-2', name: 'Token' },
        ]

        const { form } = useAddInvestmentForm('CRYPTO')

        expect(form.typeId).toBe('')
      })

      it('is sent with the segment when chosen and left out when not', async () => {
        useTypesListStore().holdingTypes = [mockCryptoType]
        useTypesListStore().segments = [{ id: 'segment-1', name: 'Energia', usageCount: 0 }]
        const { form, submit } = useAddInvestmentForm('CRYPTO')
        fillCryptoPurchase(form)

        await submit()
        expect(holdingsApiModule.holdingsApi.createCryptoHolding).toHaveBeenLastCalledWith(
          'wallet-crypto-1',
          expect.objectContaining({ typeId: 'type-crypto-1', segmentId: undefined }),
        )

        form.typeId = ''
        form.segmentId = 'segment-1'
        await submit()
        expect(holdingsApiModule.holdingsApi.createCryptoHolding).toHaveBeenLastCalledWith(
          'wallet-crypto-1',
          expect.objectContaining({ typeId: undefined, segmentId: 'segment-1' }),
        )
      })
    })
  })

  it('submit calls createStockHolding with correct payload', async () => {
    vi.mocked(holdingsApiModule.holdingsApi.createStockHolding).mockResolvedValue({
      id: 'new-holding-id',
      walletId: 'wallet-stocks-1',
      typeId: 'type-1',
      segmentId: null,
      typeName: 'Ação Ordinária',
      segmentName: null,
      ticker: 'PETR4',
      name: '',
      currentPrice: null,
      lots: [],
      frozen: false,
      withdrawals: [],
    })

    const { form, submit } = useAddInvestmentForm('STOCKS')
    form.ticker = 'PETR4'
    form.quantity = 10
    form.price = 36.5
    form.date = new Date('2024-06-01')
    form.typeId = 'type-1'

    await submit()

    expect(holdingsApiModule.holdingsApi.createStockHolding).toHaveBeenCalledWith(
      'wallet-stocks-1',
      expect.objectContaining({
        ticker: 'PETR4',
        typeId: 'type-1',
        lot: expect.objectContaining({
          quantity: 10,
          price: 36.5,
          lotDate: '2024-06-01',
        }),
      }),
    )
  })

  it('sends the chosen segment and leaves it out when none is chosen', async () => {
    vi.mocked(holdingsApiModule.holdingsApi.createStockHolding).mockResolvedValue(
      {} as Awaited<ReturnType<typeof holdingsApiModule.holdingsApi.createStockHolding>>,
    )
    const typesListStore = useTypesListStore()
    typesListStore.segments = [{ id: 'segment-1', name: 'Energia', usageCount: 0 }]

    const { form, submit } = useAddInvestmentForm('STOCKS')
    expect(form.segmentId).toBe('')
    form.ticker = 'TAEE11'
    form.quantity = 1
    form.price = 30
    form.date = new Date('2024-06-01')

    await submit()
    expect(holdingsApiModule.holdingsApi.createStockHolding).toHaveBeenLastCalledWith(
      'wallet-stocks-1',
      expect.objectContaining({ segmentId: undefined }),
    )

    form.segmentId = 'segment-1'
    await submit()
    expect(holdingsApiModule.holdingsApi.createStockHolding).toHaveBeenLastCalledWith(
      'wallet-stocks-1',
      expect.objectContaining({ segmentId: 'segment-1' }),
    )
  })

  it('drops the chosen segment when it disappears from the list', async () => {
    const typesListStore = useTypesListStore()
    typesListStore.segments = [{ id: 'segment-1', name: 'Energia', usageCount: 0 }]
    const { form } = useAddInvestmentForm('STOCKS')
    form.segmentId = 'segment-1'

    typesListStore.segments = [{ id: 'segment-2', name: 'Tecnologia', usageCount: 0 }]
    await nextTick()

    expect(form.segmentId).toBe('')
  })
})
