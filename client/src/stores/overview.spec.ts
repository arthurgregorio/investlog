import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useOverviewStore } from './overview'
import * as overviewApiModule from '@/api/overview'
import type { PortfolioSummary, SeriesPoint } from '@/types'

vi.mock('@/api/overview')

const mockSummary: PortfolioSummary = {
  baseCurrency: 'BRL',
  totalCostBasis: 10000,
  totalCurrentValue: 11000,
  totalGain: 1000,
  totalGainPct: 10,
  kindSummaries: [
    {
      kind: 'stocks',
      holdingCount: 3,
      totalCostBasis: 6000,
      totalCurrentValue: 6600,
      totalGain: 600,
      totalGainPct: 10,
    },
  ],
}

const mockSeries: SeriesPoint[] = [
  { month: '2024-01', totalInvested: 5000 },
  { month: '2024-02', totalInvested: 10000 },
]

describe('useOverviewStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(overviewApiModule.overviewApi.getSummary).mockResolvedValue(mockSummary)
    vi.mocked(overviewApiModule.overviewApi.getSeries).mockResolvedValue(mockSeries)
  })

  it('starts with null summary and empty series', () => {
    const store = useOverviewStore()
    expect(store.summary).toBeNull()
    expect(store.series).toEqual([])
    expect(store.loaded).toBe(false)
  })

  it('load() fetches summary and series in parallel', async () => {
    const store = useOverviewStore()
    await store.load()

    expect(store.summary).toEqual(mockSummary)
    expect(store.series).toEqual(mockSeries)
    expect(store.loaded).toBe(true)
    expect(store.loading).toBe(false)
  })

  it('load() is a no-op when already loaded', async () => {
    const store = useOverviewStore()
    await store.load()
    await store.load()

    expect(overviewApiModule.overviewApi.getSummary).toHaveBeenCalledTimes(1)
  })

  it('refresh() re-fetches even when already loaded', async () => {
    const store = useOverviewStore()
    await store.load()
    await store.refresh()

    expect(overviewApiModule.overviewApi.getSummary).toHaveBeenCalledTimes(2)
  })

  it('summary.totalCostBasis is correct after load', async () => {
    const store = useOverviewStore()
    await store.load()

    expect(store.summary?.totalCostBasis).toBe(10000)
  })

  it('series is sorted ascending by month', async () => {
    const store = useOverviewStore()
    await store.load()

    expect(store.series[0].month).toBe('2024-01')
    expect(store.series[1].month).toBe('2024-02')
    expect(store.series[1].totalInvested).toBeGreaterThan(store.series[0].totalInvested)
  })
})
