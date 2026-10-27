import { describe, expect, it, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useTypesListStore } from './typesList'
import { assetTypesApi } from '@/api/assetTypes'
import type { HoldingType } from '@/types'

vi.mock('@/api/assetTypes', () => ({
  assetTypesApi: {
    findAllHoldingTypes: vi.fn(),
    createHoldingType: vi.fn(),
    updateHoldingType: vi.fn(),
    removeHoldingType: vi.fn(),
    findAllSegments: vi.fn(),
    createSegment: vi.fn(),
    updateSegment: vi.fn(),
    removeSegment: vi.fn(),
  },
}))

const stockType: HoldingType = {
  id: 'stock-1',
  name: 'Ação Ordinária',
  kinds: ['STOCKS'],
  usageCount: 3,
}
const sharedType: HoldingType = {
  id: 'shared-1',
  name: 'Renda Variável',
  kinds: ['STOCKS', 'CRYPTO', 'FUNDS'],
  usageCount: 1,
}
const fundType: HoldingType = { id: 'fund-1', name: 'Renda Fixa', kinds: ['FUNDS'], usageCount: 0 }
const segment = { id: 'segment-1', name: 'Tecnologia', usageCount: 2 }

describe('typesList store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(assetTypesApi.findAllHoldingTypes).mockResolvedValue([])
    vi.mocked(assetTypesApi.findAllSegments).mockResolvedValue([])
  })

  it('loads the holding types and the segments together', async () => {
    vi.mocked(assetTypesApi.findAllHoldingTypes).mockResolvedValue([stockType, fundType])
    vi.mocked(assetTypesApi.findAllSegments).mockResolvedValue([segment])

    const store = useTypesListStore()
    await store.load()

    expect(store.holdingTypes).toEqual([stockType, fundType])
    expect(store.segments).toEqual([segment])
    expect(store.loaded).toBe(true)
  })

  it('typesForKind keeps the types that apply to the kind, including shared ones', async () => {
    vi.mocked(assetTypesApi.findAllHoldingTypes).mockResolvedValue([
      stockType,
      sharedType,
      fundType,
    ])

    const store = useTypesListStore()
    await store.load()

    expect(store.typesForKind('STOCKS').map((type) => type.id)).toEqual(['stock-1', 'shared-1'])
    expect(store.typesForKind('CRYPTO').map((type) => type.id)).toEqual(['shared-1'])
    expect(store.typesForKind('FUNDS').map((type) => type.id)).toEqual(['shared-1', 'fund-1'])
  })

  it('load fetches only once until refresh is called', async () => {
    const store = useTypesListStore()
    await store.load()
    await store.load()
    expect(assetTypesApi.findAllHoldingTypes).toHaveBeenCalledTimes(1)

    await store.refresh()
    expect(assetTypesApi.findAllHoldingTypes).toHaveBeenCalledTimes(2)
    expect(assetTypesApi.findAllSegments).toHaveBeenCalledTimes(2)
  })

  it('load clears the loading flag and keeps the lists empty when a request fails', async () => {
    vi.mocked(assetTypesApi.findAllHoldingTypes).mockResolvedValue([stockType])
    vi.mocked(assetTypesApi.findAllSegments).mockRejectedValue(new Error('network'))

    const store = useTypesListStore()
    await expect(store.load()).rejects.toThrow('network')

    expect(store.loading).toBe(false)
    expect(store.loaded).toBe(false)
    expect(store.holdingTypes).toEqual([])
    expect(store.segments).toEqual([])
  })

  it('reset empties both lists and the flags', async () => {
    vi.mocked(assetTypesApi.findAllHoldingTypes).mockResolvedValue([stockType])
    vi.mocked(assetTypesApi.findAllSegments).mockResolvedValue([segment])

    const store = useTypesListStore()
    await store.load()
    store.reset()

    expect(store.holdingTypes).toEqual([])
    expect(store.segments).toEqual([])
    expect(store.loaded).toBe(false)
  })

  it('addHoldingType sends the kinds and appends the created type', async () => {
    vi.mocked(assetTypesApi.findAllHoldingTypes).mockResolvedValue([stockType])
    const created: HoldingType = {
      id: 'fund-2',
      name: 'Multimercado',
      kinds: ['CRYPTO', 'FUNDS'],
      usageCount: 0,
    }
    vi.mocked(assetTypesApi.createHoldingType).mockResolvedValue(created)

    const store = useTypesListStore()
    await store.load()
    const result = await store.addHoldingType('Multimercado', ['CRYPTO', 'FUNDS'])

    expect(assetTypesApi.createHoldingType).toHaveBeenCalledWith('Multimercado', [
      'CRYPTO',
      'FUNDS',
    ])
    expect(result).toEqual(created)
    expect(store.holdingTypes).toEqual([stockType, created])
  })

  it('updateHoldingType replaces the type in place', async () => {
    vi.mocked(assetTypesApi.findAllHoldingTypes).mockResolvedValue([stockType, fundType])
    const updated: HoldingType = { ...stockType, name: 'Ações', kinds: ['STOCKS', 'FUNDS'] }
    vi.mocked(assetTypesApi.updateHoldingType).mockResolvedValue(updated)

    const store = useTypesListStore()
    await store.load()
    await store.updateHoldingType(stockType.id, 'Ações', ['STOCKS', 'FUNDS'])

    expect(assetTypesApi.updateHoldingType).toHaveBeenCalledWith(stockType.id, 'Ações', [
      'STOCKS',
      'FUNDS',
    ])
    expect(store.holdingTypes).toEqual([updated, fundType])
  })

  it('removeHoldingType drops the type from the list', async () => {
    vi.mocked(assetTypesApi.findAllHoldingTypes).mockResolvedValue([stockType, fundType])
    vi.mocked(assetTypesApi.removeHoldingType).mockResolvedValue(undefined)

    const store = useTypesListStore()
    await store.load()
    await store.removeHoldingType(stockType.id)

    expect(assetTypesApi.removeHoldingType).toHaveBeenCalledWith(stockType.id)
    expect(store.holdingTypes).toEqual([fundType])
  })

  it('leaves the list untouched when a create, update or remove fails', async () => {
    vi.mocked(assetTypesApi.findAllHoldingTypes).mockResolvedValue([stockType])
    vi.mocked(assetTypesApi.createHoldingType).mockRejectedValue(new Error('duplicate'))
    vi.mocked(assetTypesApi.updateHoldingType).mockRejectedValue(new Error('in use'))
    vi.mocked(assetTypesApi.removeHoldingType).mockRejectedValue(new Error('in use'))

    const store = useTypesListStore()
    await store.load()

    await expect(store.addHoldingType('Ação Ordinária', ['STOCKS'])).rejects.toThrow('duplicate')
    await expect(store.updateHoldingType(stockType.id, 'x', ['FUNDS'])).rejects.toThrow('in use')
    await expect(store.removeHoldingType(stockType.id)).rejects.toThrow('in use')

    expect(store.holdingTypes).toEqual([stockType])
  })

  it('addSegment, updateSegment and removeSegment keep the list in step', async () => {
    vi.mocked(assetTypesApi.findAllSegments).mockResolvedValue([segment])
    const created = { id: 'segment-2', name: 'Energia', usageCount: 0 }
    const renamed = { ...segment, name: 'Tecnologia da Informação' }
    vi.mocked(assetTypesApi.createSegment).mockResolvedValue(created)
    vi.mocked(assetTypesApi.updateSegment).mockResolvedValue(renamed)
    vi.mocked(assetTypesApi.removeSegment).mockResolvedValue(undefined)

    const store = useTypesListStore()
    await store.load()

    expect(await store.addSegment('Energia')).toEqual(created)
    expect(store.segments).toEqual([segment, created])

    await store.updateSegment(segment.id, 'Tecnologia da Informação')
    expect(assetTypesApi.updateSegment).toHaveBeenCalledWith(segment.id, 'Tecnologia da Informação')
    expect(store.segments).toEqual([renamed, created])

    await store.removeSegment(created.id)
    expect(assetTypesApi.removeSegment).toHaveBeenCalledWith(created.id)
    expect(store.segments).toEqual([renamed])
  })
})
