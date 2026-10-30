import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useStrategiesStore } from './strategies'
import { strategiesApi } from '@/api/strategies'
import { classHolding, defaultStrategy, strategy } from '@/test/strategyFixtures'

vi.mock('@/api/strategies', () => ({
  strategiesApi: {
    findAll: vi.fn(),
    create: vi.fn(),
    rename: vi.fn(),
    remove: vi.fn(),
    replaceClasses: vi.fn(),
    assignHoldings: vi.fn(),
    updateTargets: vi.fn(),
  },
}))

describe('strategies store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(strategiesApi.findAll).mockResolvedValue([strategy(), defaultStrategy()])
  })

  it('loads once and finds the default strategy and any strategy by id', async () => {
    const store = useStrategiesStore()
    await store.load()
    await store.load()

    expect(strategiesApi.findAll).toHaveBeenCalledTimes(1)
    expect(store.defaultStrategy?.name).toBe('Sem estratégia')
    expect(store.strategyById('strategy-long')?.name).toBe('Longo prazo')
    expect(store.loaded).toBe(true)
  })

  it('keeps loading false after a failed load so a retry can run', async () => {
    vi.mocked(strategiesApi.findAll).mockRejectedValueOnce(new Error('offline'))
    const store = useStrategiesStore()

    await expect(store.load()).rejects.toThrow('offline')
    expect(store.loading).toBe(false)
    expect(store.loaded).toBe(false)
  })

  it('puts a created strategy before the default one', async () => {
    vi.mocked(strategiesApi.create).mockResolvedValue(
      strategy({ id: 'strategy-new', name: 'Aposentadoria', classes: [] }),
    )
    const store = useStrategiesStore()
    await store.load()

    const created = await store.create('Aposentadoria')

    expect(created.id).toBe('strategy-new')
    expect(store.strategies.map((item) => item.name)).toEqual([
      'Longo prazo',
      'Aposentadoria',
      'Sem estratégia',
    ])
  })

  it('replaces a strategy after renaming it or saving its classes', async () => {
    vi.mocked(strategiesApi.rename).mockResolvedValue(strategy({ name: 'Longo prazo 2' }))
    vi.mocked(strategiesApi.replaceClasses).mockResolvedValue(
      strategy({ name: 'Longo prazo 2', classes: [] }),
    )
    const store = useStrategiesStore()
    await store.load()

    await store.rename('strategy-long', 'Longo prazo 2')
    expect(store.strategyById('strategy-long')?.name).toBe('Longo prazo 2')

    await store.saveClasses('strategy-long', [])
    expect(strategiesApi.replaceClasses).toHaveBeenCalledWith('strategy-long', [])
    expect(store.strategyById('strategy-long')?.classes).toEqual([])
    expect(store.strategyById('strategy-default')?.default).toBe(true)
  })

  it('reloads after removing a strategy, assigning holdings and saving targets', async () => {
    vi.mocked(strategiesApi.remove).mockResolvedValue()
    vi.mocked(strategiesApi.assignHoldings).mockResolvedValue()
    vi.mocked(strategiesApi.updateTargets).mockResolvedValue([classHolding()])
    const store = useStrategiesStore()
    await store.load()

    await store.remove('strategy-long')
    await store.assignHoldings('class-brazil', [{ kind: 'STOCKS', id: 'holding-petr4' }])
    const holdings = await store.saveTargets('class-brazil', [
      { kind: 'STOCKS', holdingId: 'holding-petr4', targetPercentage: 30 },
    ])

    expect(strategiesApi.remove).toHaveBeenCalledWith('strategy-long')
    expect(strategiesApi.assignHoldings).toHaveBeenCalledWith('class-brazil', [
      { kind: 'STOCKS', id: 'holding-petr4' },
    ])
    expect(holdings).toEqual([classHolding()])
    expect(strategiesApi.findAll).toHaveBeenCalledTimes(4)
  })

  it('resets to an empty, unloaded state', async () => {
    const store = useStrategiesStore()
    await store.load()

    store.reset()

    expect(store.strategies).toEqual([])
    expect(store.loaded).toBe(false)
  })
})
