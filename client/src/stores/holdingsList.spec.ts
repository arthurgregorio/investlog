import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useHoldingsListStore } from './holdingsList'
import * as holdingsApiModule from '@/api/holdings'
import type { HoldingRow, PagedResponse } from '@/types'

vi.mock('@/api/holdings')

const makeRow = (id: string, kind: 'STOCKS' | 'CRYPTO' | 'FUNDS' = 'STOCKS'): HoldingRow => ({
  id,
  kind,
  name: 'Test Holding',
  ticker: 'TEST3',
  typeLabel: 'Ação',
  walletId: 'wallet-1',
  walletName: 'Test Wallet',
  walletCurrency: 'BRL',
  quantity: 10,
  costBasis: 1000,
  currentValue: 1100,
  gain: 100,
  gainPct: 10,
})

const makePagedResponse = (rows: HoldingRow[]): PagedResponse<HoldingRow> => ({
  content: rows,
  page: { size: 20, number: 0, totalElements: rows.length, totalPages: 1 },
})

describe('useHoldingsListStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('starts with empty state', () => {
    const store = useHoldingsListStore()
    expect(store.rows).toEqual([])
    expect(store.totalElements).toBe(0)
  })

  it('loadKind fetches all holdings when kind=all', async () => {
    const rows = [makeRow('h1', 'STOCKS'), makeRow('h2', 'CRYPTO')]
    vi.mocked(holdingsApiModule.holdingsApi.findAll).mockResolvedValue(makePagedResponse(rows))

    const store = useHoldingsListStore()
    await store.loadKind('all')

    expect(holdingsApiModule.holdingsApi.findAll).toHaveBeenCalledWith({
      kind: undefined,
      page: 0,
      size: 20,
    })
    expect(store.rows).toEqual(rows)
    expect(store.totalElements).toBe(2)
  })

  it('loadKind passes kind filter for specific tabs', async () => {
    vi.mocked(holdingsApiModule.holdingsApi.findAll).mockResolvedValue(
      makePagedResponse([makeRow('h1', 'FUNDS')]),
    )

    const store = useHoldingsListStore()
    await store.loadKind('FUNDS')

    expect(holdingsApiModule.holdingsApi.findAll).toHaveBeenCalledWith({
      kind: 'FUNDS',
      page: 0,
      size: 20,
    })
    expect(store.currentKind).toBe('FUNDS')
  })

  it('loadKind with page number passes it to the API', async () => {
    vi.mocked(holdingsApiModule.holdingsApi.findAll).mockResolvedValue(makePagedResponse([]))

    const store = useHoldingsListStore()
    await store.loadKind('STOCKS', 2)

    expect(holdingsApiModule.holdingsApi.findAll).toHaveBeenCalledWith({
      kind: 'STOCKS',
      page: 2,
      size: 20,
    })
    expect(store.page).toBe(2)
  })

  it('refresh re-fetches with same kind and page', async () => {
    vi.mocked(holdingsApiModule.holdingsApi.findAll).mockResolvedValue(makePagedResponse([]))

    const store = useHoldingsListStore()
    await store.loadKind('CRYPTO', 1)
    await store.refresh()

    expect(holdingsApiModule.holdingsApi.findAll).toHaveBeenCalledTimes(2)
    expect(holdingsApiModule.holdingsApi.findAll).toHaveBeenLastCalledWith({
      kind: 'CRYPTO',
      page: 1,
      size: 20,
    })
  })

  it('loadKind passes typeLabel, search and sort through to the API', async () => {
    vi.mocked(holdingsApiModule.holdingsApi.findAll).mockResolvedValue(makePagedResponse([]))

    const store = useHoldingsListStore()
    await store.loadKind('STOCKS', 0, { typeLabel: 'Ação ON', search: 'PETR', sort: 'invested,asc' })

    expect(holdingsApiModule.holdingsApi.findAll).toHaveBeenCalledWith({
      kind: 'STOCKS',
      typeLabel: 'Ação ON',
      search: 'PETR',
      sort: 'invested,asc',
      page: 0,
      size: 20,
    })
  })

  it('refresh re-fetches with the same typeLabel, search and sort', async () => {
    vi.mocked(holdingsApiModule.holdingsApi.findAll).mockResolvedValue(makePagedResponse([]))

    const store = useHoldingsListStore()
    await store.loadKind('STOCKS', 0, { typeLabel: 'Ação ON', search: 'PETR', sort: 'invested,asc' })
    await store.refresh()

    expect(holdingsApiModule.holdingsApi.findAll).toHaveBeenLastCalledWith({
      kind: 'STOCKS',
      typeLabel: 'Ação ON',
      search: 'PETR',
      sort: 'invested,asc',
      page: 0,
      size: 20,
    })
  })
})
