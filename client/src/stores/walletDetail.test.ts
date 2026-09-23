import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useWalletDetailStore } from './walletDetail'
import { walletDetailApi } from '@/api/walletDetail'
import type { WalletDetail } from '@/types'

vi.mock('@/api/walletDetail', () => ({
  walletDetailApi: { get: vi.fn() },
}))

const mockDetail: WalletDetail = {
  id: 'wallet-1',
  name: 'Detail Wallet',
  kind: 'STOCKS',
  currency: 'BRL',
  currentValue: 4750,
  totalInvested: 4500,
  gain: 250,
  gainPct: 5.5556,
  series: [],
  dayChange: null,
  weekChange: null,
  monthChange: null,
  bestPerformer: null,
  worstPerformer: null,
  largestHoldingName: null,
  largestHoldingShare: null,
  activity: {
    lastTransactionDate: null,
    lastTransactionName: null,
    lastTransactionAmount: null,
    transactionCount: 0,
    walletAgeInDays: null,
    investmentCount: 0,
  },
}

describe('useWalletDetailStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('starts unloaded with no detail', () => {
    const store = useWalletDetailStore()

    expect(store.detail).toBeNull()
    expect(store.loaded).toBe(false)
    expect(store.loading).toBe(false)
  })

  it('load() fetches the detail for the given wallet', async () => {
    vi.mocked(walletDetailApi.get).mockResolvedValue(mockDetail)
    const store = useWalletDetailStore()

    await store.load('wallet-1')

    expect(walletDetailApi.get).toHaveBeenCalledWith('wallet-1')
    expect(store.detail).toEqual(mockDetail)
    expect(store.loaded).toBe(true)
    expect(store.loading).toBe(false)
  })

  it('refresh() re-requests the wallet currently held', async () => {
    vi.mocked(walletDetailApi.get).mockResolvedValue(mockDetail)
    const store = useWalletDetailStore()
    await store.load('wallet-1')
    vi.mocked(walletDetailApi.get).mockClear()

    await store.refresh()

    expect(walletDetailApi.get).toHaveBeenCalledWith('wallet-1')
  })

  it('refresh() is a no-op before anything is loaded', async () => {
    const store = useWalletDetailStore()

    await store.refresh()

    expect(walletDetailApi.get).not.toHaveBeenCalled()
  })

  it('reset() clears the detail so a stale wallet never renders', async () => {
    vi.mocked(walletDetailApi.get).mockResolvedValue(mockDetail)
    const store = useWalletDetailStore()
    await store.load('wallet-1')

    store.reset()

    expect(store.detail).toBeNull()
    expect(store.loaded).toBe(false)
  })

  it('clears loading when the request fails', async () => {
    vi.mocked(walletDetailApi.get).mockRejectedValue(new Error('boom'))
    const store = useWalletDetailStore()

    await expect(store.load('wallet-1')).rejects.toThrow('boom')

    expect(store.loading).toBe(false)
  })
})
