import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useRatesStore } from './rates'
import { ratesApi } from '@/api/rates'

vi.mock('@/api/rates', () => ({
  ratesApi: {
    findAll: vi.fn(),
    upsert: vi.fn(),
  },
}))

const brlRate = { currencyCode: 'BRL', rate: 1, isBase: true }
const usdRate = { currencyCode: 'USD', rate: 5, isBase: false }

describe('rates store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loads the rates and marks the store as loaded', async () => {
    vi.mocked(ratesApi.findAll).mockResolvedValue([brlRate, usdRate])

    const store = useRatesStore()
    await store.load()

    expect(store.rates).toEqual([brlRate, usdRate])
    expect(store.loaded).toBe(true)
    expect(store.loading).toBe(false)
    expect(store.currencyCodes).toEqual(['BRL', 'USD'])
  })

  it('fetches once when load is called twice in a row', async () => {
    vi.mocked(ratesApi.findAll).mockResolvedValue([brlRate])

    const store = useRatesStore()
    await store.load()
    await store.load()

    expect(ratesApi.findAll).toHaveBeenCalledTimes(1)
  })

  it('fetches again on refresh regardless of the loaded flag', async () => {
    vi.mocked(ratesApi.findAll).mockResolvedValueOnce([brlRate])
    vi.mocked(ratesApi.findAll).mockResolvedValueOnce([brlRate, usdRate])

    const store = useRatesStore()
    await store.load()
    await store.refresh()

    expect(ratesApi.findAll).toHaveBeenCalledTimes(2)
    expect(store.rates).toEqual([brlRate, usdRate])
  })

  it('clears the loading flag and stays unloaded when the request fails', async () => {
    vi.mocked(ratesApi.findAll).mockRejectedValue(new Error('network'))

    const store = useRatesStore()
    await expect(store.load()).rejects.toThrow('network')

    expect(store.loading).toBe(false)
    expect(store.loaded).toBe(false)
    expect(store.rates).toEqual([])
  })

  it('reports BRL as the base currency when no rate is loaded', () => {
    const store = useRatesStore()

    expect(store.baseCurrency).toBe('BRL')
  })

  it('reports the currency flagged as base', async () => {
    vi.mocked(ratesApi.findAll).mockResolvedValue([
      { currencyCode: 'BRL', rate: 0.2, isBase: false },
      { currencyCode: 'USD', rate: 1, isBase: true },
    ])

    const store = useRatesStore()
    await store.load()

    expect(store.baseCurrency).toBe('USD')
  })

  it('upsertRate replaces a rate that is already in the list', async () => {
    vi.mocked(ratesApi.findAll).mockResolvedValue([brlRate, usdRate])
    const updatedUsdRate = { ...usdRate, rate: 5.5 }
    vi.mocked(ratesApi.upsert).mockResolvedValue(updatedUsdRate)

    const store = useRatesStore()
    await store.load()
    await store.upsertRate('USD', 5.5, false)

    expect(ratesApi.upsert).toHaveBeenCalledWith('USD', 5.5, false)
    expect(store.rates).toEqual([brlRate, updatedUsdRate])
  })

  it('upsertRate appends a rate that is not in the list yet', async () => {
    vi.mocked(ratesApi.findAll).mockResolvedValue([brlRate])
    const eurRate = { currencyCode: 'EUR', rate: 6, isBase: false }
    vi.mocked(ratesApi.upsert).mockResolvedValue(eurRate)

    const store = useRatesStore()
    await store.load()
    await store.upsertRate('EUR', 6, false)

    expect(store.rates).toEqual([brlRate, eurRate])
  })

  it('upsertRate demotes the previous base when a new base is set', async () => {
    vi.mocked(ratesApi.findAll).mockResolvedValue([brlRate, usdRate])
    const newBase = { currencyCode: 'USD', rate: 1, isBase: true }
    vi.mocked(ratesApi.upsert).mockResolvedValue(newBase)

    const store = useRatesStore()
    await store.load()
    await store.upsertRate('USD', 1, true)

    expect(store.rates).toEqual([{ ...brlRate, isBase: false }, newBase])
    expect(store.baseCurrency).toBe('USD')
  })

  it('upsertRate leaves the list untouched when the request fails', async () => {
    vi.mocked(ratesApi.findAll).mockResolvedValue([brlRate, usdRate])
    vi.mocked(ratesApi.upsert).mockRejectedValue(new Error('invalid rate'))

    const store = useRatesStore()
    await store.load()
    await expect(store.upsertRate('USD', -1, false)).rejects.toThrow('invalid rate')

    expect(store.rates).toEqual([brlRate, usdRate])
  })
})
