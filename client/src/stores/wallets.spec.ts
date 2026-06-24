import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useWalletsStore } from './wallets'
import * as walletsApiModule from '@/api/wallets'
import type { WalletResponse } from '@/types'

vi.mock('@/api/wallets')

const mockWallet: WalletResponse = {
  id: 'wallet-1',
  name: 'Test Wallet',
  kind: 'STOCKS',
  currency: 'BRL',
  holdingCount: 3,
  totalInvested: 1500,
  currentValue: null,
  gain: null,
  gainPct: null,
  createdAt: '2024-01-01T00:00:00Z',
}

describe('useWalletsStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('starts unloaded with empty wallets', () => {
    const store = useWalletsStore()
    expect(store.wallets).toEqual([])
    expect(store.loaded).toBe(false)
    expect(store.loading).toBe(false)
  })

  it('load() fetches wallets and sets loaded=true', async () => {
    vi.mocked(walletsApiModule.walletsApi.findAll).mockResolvedValue({
      content: [mockWallet],
      page: { size: 100, number: 0, totalElements: 1, totalPages: 1 },
    })

    const store = useWalletsStore()
    await store.load()

    expect(store.wallets).toEqual([mockWallet])
    expect(store.loaded).toBe(true)
    expect(store.loading).toBe(false)
  })

  it('load() is a no-op when already loaded', async () => {
    vi.mocked(walletsApiModule.walletsApi.findAll).mockResolvedValue({
      content: [mockWallet],
      page: { size: 100, number: 0, totalElements: 1, totalPages: 1 },
    })

    const store = useWalletsStore()
    await store.load()
    await store.load()

    expect(walletsApiModule.walletsApi.findAll).toHaveBeenCalledTimes(1)
  })

  it('refresh() re-fetches even when already loaded', async () => {
    vi.mocked(walletsApiModule.walletsApi.findAll).mockResolvedValue({
      content: [mockWallet],
      page: { size: 100, number: 0, totalElements: 1, totalPages: 1 },
    })

    const store = useWalletsStore()
    await store.load()
    await store.refresh()

    expect(walletsApiModule.walletsApi.findAll).toHaveBeenCalledTimes(2)
  })

  it('walletById returns the matching wallet', async () => {
    vi.mocked(walletsApiModule.walletsApi.findAll).mockResolvedValue({
      content: [mockWallet],
      page: { size: 100, number: 0, totalElements: 1, totalPages: 1 },
    })

    const store = useWalletsStore()
    await store.load()

    expect(store.walletById('wallet-1')).toEqual(mockWallet)
    expect(store.walletById('unknown')).toBeUndefined()
  })
})
