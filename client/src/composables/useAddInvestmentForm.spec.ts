import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAddInvestmentForm } from './useAddInvestmentForm'
import { useWalletsStore } from '@/stores/wallets'
import { useTypesListStore } from '@/stores/typesList'
import * as holdingsApiModule from '@/api/holdings'
import type { WalletResponse, AssetType } from '@/types'

vi.mock('@/api/holdings')
vi.mock('buefy', () => ({
  ToastProgrammatic: { open: vi.fn() },
}))

const mockWallet: WalletResponse = {
  id: 'wallet-stocks-1',
  name: 'Carteira Ações',
  kind: 'STOCKS',
  currency: 'BRL',
  holdingCount: 0,
  totalInvested: 0,
  createdAt: '2024-01-01T00:00:00Z',
}

const mockFundWallet: WalletResponse = {
  id: 'wallet-funds-1',
  name: 'Carteira Fundos',
  kind: 'FUNDS',
  currency: 'BRL',
  holdingCount: 0,
  totalInvested: 0,
  createdAt: '2024-01-01T00:00:00Z',
}

const mockStockType: AssetType = { id: 'type-1', name: 'Ação Ordinária' }
const mockFundType: AssetType = { id: 'type-fund-1', name: 'Renda Fixa' }

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

  it('submit calls createStockHolding with correct payload', async () => {
    vi.mocked(holdingsApiModule.holdingsApi.createStockHolding).mockResolvedValue({
      id: 'new-holding-id',
      walletId: 'wallet-stocks-1',
      stockTypeId: 'type-1',
      ticker: 'PETR4',
      name: '',
      currentPrice: null,
      lots: [],
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
