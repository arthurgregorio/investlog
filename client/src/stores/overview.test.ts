import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useOverviewStore } from './overview'
import * as overviewApiModule from '@/api/overview'
import type { MonthSummary, PortfolioSummary, SeriesPoint } from '@/types'

vi.mock('@/api/overview')

const mockSummary: PortfolioSummary = {
  displayCurrency: 'BRL',
  totalCostBasis: 10000,
  totalCurrentValue: 11000,
  totalCash: 0,
  totalGain: 1000,
  totalGainPct: 10,
  positionsValue: 11000,
  ownMoney: 10000,
  realizedProfit: 0,
  lifetimeResult: 1000,
  lifetimeResultPct: 10,
  unpricedHoldingCount: 0,
  cashWalletCount: 0,
  kindSummaries: [
    {
      kind: 'STOCKS',
      holdingCount: 3,
      walletCount: 1,
      totalCostBasis: 6000,
      totalCurrentValue: 6600,
      totalGain: 600,
      totalGainPct: 10,
    },
  ],
}

const mockSeries: SeriesPoint[] = [
  {
    month: '2024-01',
    ownMoney: 5000,
    reinvestedProfit: 0,
    totalInvested: 5000,
    result: 0,
    marketValued: false,
  },
  {
    month: '2024-02',
    ownMoney: 10000,
    reinvestedProfit: 0,
    totalInvested: 10000,
    result: 1000,
    marketValued: true,
  },
]

const mockMonth: MonthSummary = {
  month: '2024-02',
  contributions: 5000,
  realizedProfit: 0,
  currentResultChange: 200,
  bestMover: null,
  worstMover: null,
}

describe('useOverviewStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(overviewApiModule.overviewApi.getSummary).mockResolvedValue(mockSummary)
    vi.mocked(overviewApiModule.overviewApi.getSeries).mockResolvedValue(mockSeries)
    vi.mocked(overviewApiModule.overviewApi.getMonth).mockResolvedValue(mockMonth)
  })

  it('starts with null summary and empty series', () => {
    const store = useOverviewStore()
    expect(store.summary).toBeNull()
    expect(store.series).toEqual([])
    expect(store.loaded).toBe(false)
  })

  it('load() fetches summary, series and the month in parallel', async () => {
    const store = useOverviewStore()
    await store.load()

    expect(store.summary).toEqual(mockSummary)
    expect(store.series).toEqual(mockSeries)
    expect(store.month).toEqual(mockMonth)
    expect(store.loaded).toBe(true)
    expect(store.loading).toBe(false)
  })

  it('reset() clears the month with everything else', async () => {
    const store = useOverviewStore()
    await store.load()
    store.reset()

    expect(store.summary).toBeNull()
    expect(store.series).toEqual([])
    expect(store.month).toBeNull()
    expect(store.loaded).toBe(false)
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
