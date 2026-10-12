import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useResultsStore } from './results'
import { resultsApi } from '@/api/results'
import type { ResultRow, ResultSummary } from '@/types'

vi.mock('@/api/results', () => ({
  resultsApi: {
    findAll: vi.fn(),
    getSummary: vi.fn(),
    withdrawFromStockHolding: vi.fn(),
    withdrawFromCryptoHolding: vi.fn(),
    withdrawFromFundHolding: vi.fn(),
  },
}))

const mockResult: ResultRow = {
  id: 'result-1',
  kind: 'STOCKS',
  resultType: 'WITHDRAWAL',
  holdingName: 'Petrobras',
  ticker: 'PETR4',
  walletId: 'wallet-1',
  walletName: 'Stocks Wallet',
  walletCurrency: 'BRL',
  resultDate: '2026-09-19',
  quantity: 40,
  grossAmount: 2000,
  fees: 0,
  taxes: 0,
  costBasis: 1400,
  netAmount: 2000,
  profit: 600,
}

function pageOf(rows: ResultRow[], totalElements = rows.length) {
  return {
    content: rows,
    page: { size: 20, number: 0, totalElements, totalPages: 1 },
  }
}

describe('useResultsStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('starts unloaded with no rows', () => {
    const store = useResultsStore()

    expect(store.rows).toEqual([])
    expect(store.loaded).toBe(false)
    expect(store.loading).toBe(false)
    expect(store.totalElements).toBe(0)
  })

  it('load() fetches the first page of withdrawals and records the totals', async () => {
    vi.mocked(resultsApi.findAll).mockResolvedValue(pageOf([mockResult], 1))
    const store = useResultsStore()

    await store.load()

    expect(resultsApi.findAll).toHaveBeenCalledWith({
      page: 0,
      size: 20,
      resultType: 'WITHDRAWAL',
    })
    expect(store.rows).toEqual([mockResult])
    expect(store.totalElements).toBe(1)
    expect(store.totalPages).toBe(1)
    expect(store.loaded).toBe(true)
    expect(store.loading).toBe(false)
  })

  it('load(pageNumber) requests that page and remembers it', async () => {
    vi.mocked(resultsApi.findAll).mockResolvedValue(pageOf([], 40))
    const store = useResultsStore()

    await store.load(2)

    expect(resultsApi.findAll).toHaveBeenCalledWith({
      page: 2,
      size: 20,
      resultType: 'WITHDRAWAL',
    })
    expect(store.page).toBe(2)
  })

  it('load(pageNumber, size) requests that page size and remembers it', async () => {
    vi.mocked(resultsApi.findAll).mockResolvedValue(pageOf([mockResult], 25))
    const store = useResultsStore()

    await store.load(1, 10)

    expect(resultsApi.findAll).toHaveBeenCalledWith({
      page: 1,
      size: 10,
      resultType: 'WITHDRAWAL',
    })
    expect(store.page).toBe(1)
    expect(store.pageSize).toBe(10)
  })

  it('refresh() re-requests the page and page size currently held', async () => {
    vi.mocked(resultsApi.findAll).mockResolvedValue(pageOf([mockResult]))
    const store = useResultsStore()
    await store.load(3, 10)
    vi.mocked(resultsApi.findAll).mockClear()

    await store.refresh()

    expect(resultsApi.findAll).toHaveBeenCalledWith({
      page: 3,
      size: 10,
      resultType: 'WITHDRAWAL',
    })
  })

  it('reset() forgets the loaded withdrawals and the remembered page size', async () => {
    vi.mocked(resultsApi.findAll).mockResolvedValue(pageOf([mockResult], 25))
    const store = useResultsStore()
    await store.load(1, 10)

    store.reset()

    expect(store.rows).toEqual([])
    expect(store.page).toBe(0)
    expect(store.pageSize).toBe(20)
    expect(store.totalElements).toBe(0)
    expect(store.loaded).toBe(false)

    vi.mocked(resultsApi.findAll).mockClear()
    await store.load()

    expect(resultsApi.findAll).toHaveBeenCalledWith({
      page: 0,
      size: 20,
      resultType: 'WITHDRAWAL',
    })
  })

  it('clears loading when the request fails', async () => {
    vi.mocked(resultsApi.findAll).mockRejectedValue(new Error('boom'))
    const store = useResultsStore()

    await expect(store.load()).rejects.toThrow('boom')

    expect(store.loading).toBe(false)
    expect(store.loaded).toBe(true)
  })

  it('loadSummary() stores the realised totals', async () => {
    const summary: ResultSummary = {
      displayCurrency: 'BRL',
      totalWithdrawn: 800,
      totalNetReceived: 790,
      totalProfit: 190,
      totalFees: 7,
      totalTaxes: 3,
      exitCount: 2,
    }
    vi.mocked(resultsApi.getSummary).mockResolvedValue(summary)
    const store = useResultsStore()

    await store.loadSummary()

    expect(store.summary).toEqual(summary)
    expect(store.summaryLoading).toBe(false)
  })

  it('loadSummary() clears loading when the request fails', async () => {
    vi.mocked(resultsApi.getSummary).mockRejectedValue(new Error('boom'))
    const store = useResultsStore()

    await expect(store.loadSummary()).rejects.toThrow('boom')

    expect(store.summaryLoading).toBe(false)
    expect(store.summary).toBeNull()
  })
})
