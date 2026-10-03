import { beforeEach, describe, expect, it, vi } from 'vitest'
import { holdingsApi } from './holdings'
import { apiClient } from './client'

vi.mock('./client', () => ({ apiClient: { patch: vi.fn() } }))

describe('holdingsApi frozen updates', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(apiClient.patch).mockResolvedValue({ data: { frozen: true } })
  })

  it.each([
    ['updateStockHolding', 'stock-holdings'],
    ['updateCryptoHolding', 'crypto-holdings'],
    ['updateFundHolding', 'fund-holdings'],
  ] as const)('%s patches only the frozen flag on its own endpoint', async (method, path) => {
    const result = await holdingsApi[method]('wallet-1', 'holding-1', { frozen: true })

    expect(apiClient.patch).toHaveBeenCalledWith(`/wallets/wallet-1/${path}/holding-1`, {
      frozen: true,
    })
    expect(result).toEqual({ frozen: true })
  })
})
