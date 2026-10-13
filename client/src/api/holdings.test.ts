import { beforeEach, describe, expect, it, vi } from 'vitest'
import { holdingsApi } from './holdings'
import axios from 'axios'
import { apiClient } from './client'

vi.mock('./client', () => ({ apiClient: { patch: vi.fn(), get: vi.fn() } }))

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

describe('holdingsApi list filters', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(apiClient.get).mockResolvedValue({ data: [] })
  })

  it.each([
    ['findAll', '/holdings'],
    ['findAllForReport', '/holdings/report'],
  ] as const)('%s sends wallets and types as repeated keys', async (method, path) => {
    await holdingsApi[method]({ walletId: ['wallet-1', 'wallet-2'], typeLabel: ['FII'] })

    const [url, config] = vi.mocked(apiClient.get).mock.calls[0]
    expect(url).toBe(path)
    expect(
      axios.getUri({ url, params: config!.params, paramsSerializer: config!.paramsSerializer }),
    ).toBe(`${path}?walletId=wallet-1&walletId=wallet-2&typeLabel=FII`)
  })
})
