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

  it('load fetches only once until refresh is called', async () => {
    vi.mocked(assetTypesApi.findAllStockTypes).mockResolvedValue([stockType])
    vi.mocked(assetTypesApi.findAllFundTypes).mockResolvedValue([fundType])

    const store = useTypesListStore()
    await store.load()
    await store.load()
    expect(assetTypesApi.findAllStockTypes).toHaveBeenCalledTimes(1)

    await store.refresh()
    expect(assetTypesApi.findAllStockTypes).toHaveBeenCalledTimes(2)
    expect(assetTypesApi.findAllFundTypes).toHaveBeenCalledTimes(2)
  })

  it('load clears the loading flag and keeps the lists empty when a request fails', async () => {
    vi.mocked(assetTypesApi.findAllStockTypes).mockResolvedValue([stockType])
    vi.mocked(assetTypesApi.findAllFundTypes).mockRejectedValue(new Error('network'))

    const store = useTypesListStore()
    await expect(store.load()).rejects.toThrow('network')

    expect(store.loading).toBe(false)
    expect(store.loaded).toBe(false)
    expect(store.stockTypes).toEqual([])
    expect(store.fundTypes).toEqual([])
  })

  it('addFundType appends the created type', async () => {
    vi.mocked(assetTypesApi.findAllStockTypes).mockResolvedValue([])
    vi.mocked(assetTypesApi.findAllFundTypes).mockResolvedValue([fundType])
    const created = { id: 'fund-2', name: 'Multimercado', usageCount: 0 }
    vi.mocked(assetTypesApi.createFundType).mockResolvedValue(created)

    const store = useTypesListStore()
    await store.load()
    const result = await store.addFundType('Multimercado')

    expect(assetTypesApi.createFundType).toHaveBeenCalledWith('Multimercado')
    expect(result).toEqual(created)
    expect(store.fundTypes).toEqual([fundType, created])
  })

  it('leaves the lists untouched when a create or remove fails', async () => {
    vi.mocked(assetTypesApi.findAllStockTypes).mockResolvedValue([stockType])
    vi.mocked(assetTypesApi.findAllFundTypes).mockResolvedValue([fundType])
    vi.mocked(assetTypesApi.createStockType).mockRejectedValue(new Error('duplicate'))
    vi.mocked(assetTypesApi.removeFundType).mockRejectedValue(new Error('in use'))

    const store = useTypesListStore()
    await store.load()

    await expect(store.addStockType('Ação Ordinária')).rejects.toThrow('duplicate')
    await expect(store.removeFundType(fundType.id)).rejects.toThrow('in use')

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
