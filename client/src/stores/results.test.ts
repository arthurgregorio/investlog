import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useResultsStore } from './results'
import { resultsApi } from '@/api/results'
import type { ResultRow } from '@/types'

vi.mock('@/api/results', () => ({
  resultsApi: {
    findAll: vi.fn(),
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

  it('load() fetches the first page and records the totals', async () => {
    vi.mocked(resultsApi.findAll).mockResolvedValue(pageOf([mockResult], 1))
    const store = useResultsStore()

    await store.load()

    expect(resultsApi.findAll).toHaveBeenCalledWith({ page: 0, size: 20 })
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

    expect(resultsApi.findAll).toHaveBeenCalledWith({ page: 2, size: 20 })
    expect(store.page).toBe(2)
  })

  it('refresh() re-requests the page currently held', async () => {
    vi.mocked(resultsApi.findAll).mockResolvedValue(pageOf([mockResult]))
    const store = useResultsStore()
    await store.load(3)
    vi.mocked(resultsApi.findAll).mockClear()

    await store.refresh()

    expect(resultsApi.findAll).toHaveBeenCalledWith({ page: 3, size: 20 })
  })

  it('clears loading when the request fails', async () => {
    vi.mocked(resultsApi.findAll).mockRejectedValue(new Error('boom'))
    const store = useResultsStore()

    await expect(store.load()).rejects.toThrow('boom')

    expect(store.loading).toBe(false)
    expect(store.loaded).toBe(true)
  })
})
