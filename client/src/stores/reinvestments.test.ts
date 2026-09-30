import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useReinvestmentsStore } from './reinvestments'
import { reinvestmentsApi, type ReinvestmentPayload } from '@/api/reinvestments'
import type { ReinvestmentRow } from '@/types'

vi.mock('@/api/reinvestments', () => ({
  reinvestmentsApi: { findAll: vi.fn(), reinvest: vi.fn() },
}))

const reinvestmentRow: ReinvestmentRow = {
  id: 'reinvestment-1',
  reinvestmentDate: '2026-09-30',
  currency: 'BRL',
  source: {
    holdingId: 'holding-1',
    kind: 'STOCKS',
    name: 'Petrobras',
    ticker: 'PETR4',
    walletId: 'wallet-1',
    walletName: 'Ações',
  },
  destination: {
    holdingId: 'holding-2',
    kind: 'FUNDS',
    name: 'Tesouro Selic',
    ticker: null,
    walletId: 'wallet-2',
    walletName: 'Fundos',
  },
  quantity: 40,
  grossAmount: 2000,
  fees: 10,
  taxes: 90,
  amount: 1900,
  profit: 500,
}

const payload: ReinvestmentPayload = {
  sourceKind: 'STOCKS',
  sourceHoldingId: 'holding-1',
  destinationKind: 'FUNDS',
  destinationHoldingId: 'holding-2',
  reinvestmentDate: '2026-09-30',
  quantity: 40,
  unitPrice: 50,
  fees: 10,
  taxes: 90,
}

function pageOf(rows: ReinvestmentRow[]) {
  return { content: rows, page: { size: 5, number: 0, totalElements: rows.length, totalPages: 1 } }
}

describe('useReinvestmentsStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loads a page of the history at the requested size, so a short recent list and the full history share it', async () => {
    vi.mocked(reinvestmentsApi.findAll).mockResolvedValue(pageOf([reinvestmentRow]))
    const store = useReinvestmentsStore()

    await store.load(0, 5)

    expect(reinvestmentsApi.findAll).toHaveBeenCalledWith({ page: 0, size: 5 })
    expect(store.rows).toEqual([reinvestmentRow])
    expect(store.totalElements).toBe(1)
    expect(store.loaded).toBe(true)
  })

  it('records a reinvestment without fetching history nobody has loaded', async () => {
    vi.mocked(reinvestmentsApi.reinvest).mockResolvedValue(undefined)
    const store = useReinvestmentsStore()

    await store.reinvest(payload)

    expect(reinvestmentsApi.reinvest).toHaveBeenCalledWith(payload)
    expect(reinvestmentsApi.findAll).not.toHaveBeenCalled()
  })

  it('reloads loaded history after recording a reinvestment', async () => {
    vi.mocked(reinvestmentsApi.findAll).mockResolvedValue(pageOf([reinvestmentRow]))
    vi.mocked(reinvestmentsApi.reinvest).mockResolvedValue(undefined)
    const store = useReinvestmentsStore()
    await store.load(0, 5)

    await store.reinvest(payload)

    expect(reinvestmentsApi.findAll).toHaveBeenCalledTimes(2)
    expect(reinvestmentsApi.findAll).toHaveBeenLastCalledWith({ page: 0, size: 5 })
  })

  it('leaves the history untouched when the server rejects the reinvestment', async () => {
    vi.mocked(reinvestmentsApi.findAll).mockResolvedValue(pageOf([reinvestmentRow]))
    vi.mocked(reinvestmentsApi.reinvest).mockRejectedValue(new Error('400'))
    const store = useReinvestmentsStore()
    await store.load(0, 5)

    await expect(store.reinvest(payload)).rejects.toThrow('400')

    expect(reinvestmentsApi.findAll).toHaveBeenCalledTimes(1)
    expect(store.rows).toEqual([reinvestmentRow])
  })
})
