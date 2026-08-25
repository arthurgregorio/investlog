import { describe, expect, it, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useTypesListStore } from './typesList'
import { assetTypesApi } from '@/api/assetTypes'

vi.mock('@/api/assetTypes', () => ({
  assetTypesApi: {
    findAllStockTypes: vi.fn(),
    createStockType: vi.fn(),
    updateStockType: vi.fn(),
    removeStockType: vi.fn(),
    findAllFundTypes: vi.fn(),
    createFundType: vi.fn(),
    updateFundType: vi.fn(),
    removeFundType: vi.fn(),
  },
}))

const stockType = { id: 'stock-1', name: 'Ação Ordinária', usageCount: 3 }
const fundType = { id: 'fund-1', name: 'Renda Fixa', usageCount: 0 }

describe('typesList store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loads stock and fund types', async () => {
    vi.mocked(assetTypesApi.findAllStockTypes).mockResolvedValue([stockType])
    vi.mocked(assetTypesApi.findAllFundTypes).mockResolvedValue([fundType])

    const store = useTypesListStore()
    await store.load()

    expect(store.stockTypes).toEqual([stockType])
    expect(store.fundTypes).toEqual([fundType])
  })

  it('addStockType appends the created type', async () => {
    vi.mocked(assetTypesApi.findAllStockTypes).mockResolvedValue([])
    vi.mocked(assetTypesApi.findAllFundTypes).mockResolvedValue([])
    const created = { id: 'stock-2', name: 'ETF', usageCount: 0 }
    vi.mocked(assetTypesApi.createStockType).mockResolvedValue(created)

    const store = useTypesListStore()
    await store.load()
    await store.addStockType('ETF')

    expect(assetTypesApi.createStockType).toHaveBeenCalledWith('ETF')
    expect(store.stockTypes).toEqual([created])
  })

  it('updateStockType replaces the type in place', async () => {
    vi.mocked(assetTypesApi.findAllStockTypes).mockResolvedValue([stockType])
    vi.mocked(assetTypesApi.findAllFundTypes).mockResolvedValue([])
    const renamed = { ...stockType, name: 'Ações' }
    vi.mocked(assetTypesApi.updateStockType).mockResolvedValue(renamed)

    const store = useTypesListStore()
    await store.load()
    await store.updateStockType(stockType.id, 'Ações')

    expect(assetTypesApi.updateStockType).toHaveBeenCalledWith(stockType.id, 'Ações')
    expect(store.stockTypes[0].name).toBe('Ações')
  })

  it('removeStockType drops the type from the list', async () => {
    vi.mocked(assetTypesApi.findAllStockTypes).mockResolvedValue([stockType])
    vi.mocked(assetTypesApi.findAllFundTypes).mockResolvedValue([])
    vi.mocked(assetTypesApi.removeStockType).mockResolvedValue(undefined)

    const store = useTypesListStore()
    await store.load()
    await store.removeStockType(stockType.id)

    expect(assetTypesApi.removeStockType).toHaveBeenCalledWith(stockType.id)
    expect(store.stockTypes).toEqual([])
  })

  it('updateFundType replaces the type in place', async () => {
    vi.mocked(assetTypesApi.findAllStockTypes).mockResolvedValue([])
    vi.mocked(assetTypesApi.findAllFundTypes).mockResolvedValue([fundType])
    const renamed = { ...fundType, name: 'Multimercado' }
    vi.mocked(assetTypesApi.updateFundType).mockResolvedValue(renamed)

    const store = useTypesListStore()
    await store.load()
    await store.updateFundType(fundType.id, 'Multimercado')

    expect(assetTypesApi.updateFundType).toHaveBeenCalledWith(fundType.id, 'Multimercado')
    expect(store.fundTypes[0].name).toBe('Multimercado')
  })

  it('removeFundType drops the type from the list', async () => {
    vi.mocked(assetTypesApi.findAllStockTypes).mockResolvedValue([])
    vi.mocked(assetTypesApi.findAllFundTypes).mockResolvedValue([fundType])
    vi.mocked(assetTypesApi.removeFundType).mockResolvedValue(undefined)

    const store = useTypesListStore()
    await store.load()
    await store.removeFundType(fundType.id)

    expect(assetTypesApi.removeFundType).toHaveBeenCalledWith(fundType.id)
    expect(store.fundTypes).toEqual([])
  })
})
