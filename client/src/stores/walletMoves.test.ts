import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useWalletMovesStore } from './walletMoves'
import { walletMovesApi } from '@/api/walletMoves'
import type { WalletMoveRow } from '@/types'

vi.mock('@/api/walletMoves', () => ({
  walletMovesApi: { findAll: vi.fn(), move: vi.fn() },
}))

const moveRow: WalletMoveRow = {
  id: 'move-1',
  movedAt: '2026-09-24',
  direction: 'OUT',
  kind: 'STOCKS',
  holdingName: 'Petrobras',
  ticker: 'PETR4',
  quantity: 10,
  originWalletId: 'wallet-origin',
  originWalletName: 'Origem',
  destinationWalletId: 'wallet-destination',
  destinationWalletName: 'Destino',
}

function pageOf(rows: WalletMoveRow[]) {
  return { content: rows, page: { size: 10, number: 0, totalElements: rows.length, totalPages: 1 } }
}

describe('useWalletMovesStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loads one wallet page of move history', async () => {
    vi.mocked(walletMovesApi.findAll).mockResolvedValue(pageOf([moveRow]))
    const store = useWalletMovesStore()

    await store.load('wallet-origin')

    expect(walletMovesApi.findAll).toHaveBeenCalledWith('wallet-origin', { page: 0, size: 10 })
    expect(store.rows).toEqual([moveRow])
    expect(store.loaded).toBe(true)
  })

  it('reloads the loaded history after a move touching that wallet', async () => {
    vi.mocked(walletMovesApi.findAll).mockResolvedValue(pageOf([]))
    vi.mocked(walletMovesApi.move).mockResolvedValue()
    const store = useWalletMovesStore()
    await store.load('wallet-destination')
    vi.mocked(walletMovesApi.findAll).mockResolvedValue(pageOf([moveRow]))

    await store.move('wallet-origin', {
      destinationWalletId: 'wallet-destination',
      items: [{ holdingId: 'holding-1' }],
    })

    expect(walletMovesApi.findAll).toHaveBeenCalledTimes(2)
    expect(store.rows).toEqual([moveRow])
  })

  it('leaves the history untouched when a move is rejected', async () => {
    vi.mocked(walletMovesApi.findAll).mockResolvedValue(pageOf([moveRow]))
    vi.mocked(walletMovesApi.move).mockRejectedValue(new Error('400'))
    const store = useWalletMovesStore()
    await store.load('wallet-origin')

    await expect(
      store.move('wallet-origin', { destinationWalletId: 'wallet-other', items: [] }),
    ).rejects.toThrow()

    expect(walletMovesApi.findAll).toHaveBeenCalledTimes(1)
    expect(store.rows).toEqual([moveRow])
  })
})
