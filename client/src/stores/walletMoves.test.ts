import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useWalletMovesStore } from './walletMoves'
import { walletMovesApi } from '@/api/walletMoves'

vi.mock('@/api/walletMoves', () => ({
  walletMovesApi: { move: vi.fn() },
}))

const payload = {
  destinationWalletId: 'wallet-destination',
  items: [{ holdingId: 'holding-1' }],
}

describe('useWalletMovesStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('moves holdings out of the origin wallet', async () => {
    vi.mocked(walletMovesApi.move).mockResolvedValue()
    const store = useWalletMovesStore()

    await store.move('wallet-origin', payload)

    expect(walletMovesApi.move).toHaveBeenCalledWith('wallet-origin', payload)
  })

  it('passes a rejected move on to the caller', async () => {
    vi.mocked(walletMovesApi.move).mockRejectedValue(new Error('400'))
    const store = useWalletMovesStore()

    await expect(store.move('wallet-origin', payload)).rejects.toThrow('400')
  })
})
