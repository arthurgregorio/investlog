import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useWalletLedgerStore } from './walletLedger'
import { walletLedgerApi } from '@/api/walletLedger'
import type { WalletLedgerEntry } from '@/types'

vi.mock('@/api/walletLedger', () => ({
  walletLedgerApi: { findAll: vi.fn() },
}))

const purchase: WalletLedgerEntry = {
  id: 'lot-1',
  date: '2026-09-24',
  type: 'PURCHASE',
  kind: 'STOCKS',
  holdingId: 'holding-1',
  holdingName: 'Petrobras',
  ticker: 'PETR4',
  quantity: 10,
  amount: 380,
  profit: null,
  counterpartName: null,
}

function pageOf(rows: WalletLedgerEntry[], totalElements = rows.length) {
  return { content: rows, page: { size: 10, number: 0, totalElements, totalPages: 1 } }
}

describe('useWalletLedgerStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loads one page of a wallet ledger', async () => {
    vi.mocked(walletLedgerApi.findAll).mockResolvedValue(pageOf([purchase], 23))
    const store = useWalletLedgerStore()

    await store.load('wallet-1', 2)

    expect(walletLedgerApi.findAll).toHaveBeenCalledWith('wallet-1', { page: 2, size: 10 })
    expect(store.rows).toEqual([purchase])
    expect(store.walletId).toBe('wallet-1')
    expect(store.page).toBe(2)
    expect(store.totalElements).toBe(23)
    expect(store.loaded).toBe(true)
    expect(store.loading).toBe(false)
  })

  it('starts on the first page by default', async () => {
    vi.mocked(walletLedgerApi.findAll).mockResolvedValue(pageOf([]))
    const store = useWalletLedgerStore()

    await store.load('wallet-1')

    expect(walletLedgerApi.findAll).toHaveBeenCalledWith('wallet-1', { page: 0, size: 10 })
  })

  it('leaves loading and marks loaded when a load fails', async () => {
    vi.mocked(walletLedgerApi.findAll).mockRejectedValue(new Error('500'))
    const store = useWalletLedgerStore()

    await expect(store.load('wallet-1')).rejects.toThrow('500')

    expect(store.loading).toBe(false)
    expect(store.loaded).toBe(true)
  })

  it('refreshes the current wallet and page', async () => {
    vi.mocked(walletLedgerApi.findAll).mockResolvedValue(pageOf([]))
    const store = useWalletLedgerStore()
    await store.load('wallet-1', 3)
    vi.mocked(walletLedgerApi.findAll).mockResolvedValue(pageOf([purchase]))

    await store.refresh()

    expect(walletLedgerApi.findAll).toHaveBeenLastCalledWith('wallet-1', { page: 3, size: 10 })
    expect(store.rows).toEqual([purchase])
  })

  it('refreshes nothing before a wallet is loaded', async () => {
    const store = useWalletLedgerStore()

    await store.refresh()

    expect(walletLedgerApi.findAll).not.toHaveBeenCalled()
  })

  it('resets to its initial state', async () => {
    vi.mocked(walletLedgerApi.findAll).mockResolvedValue(pageOf([purchase]))
    const store = useWalletLedgerStore()
    await store.load('wallet-1', 1)

    store.reset()

    expect(store.rows).toEqual([])
    expect(store.walletId).toBeNull()
    expect(store.page).toBe(0)
    expect(store.totalElements).toBe(0)
    expect(store.loaded).toBe(false)
  })
})
