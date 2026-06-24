import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useCurrencyStore } from './currency'
import { useRatesStore } from './rates'
import { profileApi } from '@/api/profile'

vi.mock('@/api/profile')

describe('useCurrencyStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('returns the amount unchanged when the source currency matches the display currency', () => {
    const currencyStore = useCurrencyStore()
    currencyStore.displayCurrency = 'BRL'
    expect(currencyStore.convert(250, 'BRL')).toBe(250)
  })

  it('converts from the rates-anchor currency into a non-anchor display currency', () => {
    const ratesStore = useRatesStore()
    ratesStore.rates = [
      { currencyCode: 'BRL', rate: 1, isBase: true },
      { currencyCode: 'USD', rate: 5, isBase: false },
    ]
    const currencyStore = useCurrencyStore()
    currencyStore.displayCurrency = 'USD'
    expect(currencyStore.convert(100, 'BRL')).toBeCloseTo(20)
  })

  it('converts from a non-anchor currency into the rates-anchor display currency', () => {
    const ratesStore = useRatesStore()
    ratesStore.rates = [
      { currencyCode: 'BRL', rate: 1, isBase: true },
      { currencyCode: 'USD', rate: 5, isBase: false },
    ]
    const currencyStore = useCurrencyStore()
    currencyStore.displayCurrency = 'BRL'
    expect(currencyStore.convert(20, 'USD')).toBeCloseTo(100)
  })

  it('falls back to a 1:1 rate when the currency has no configured row', () => {
    const ratesStore = useRatesStore()
    ratesStore.rates = [{ currencyCode: 'BRL', rate: 1, isBase: true }]
    const currencyStore = useCurrencyStore()
    currencyStore.displayCurrency = 'EUR'
    expect(currencyStore.convert(100, 'BRL')).toBe(100)
  })

  it('setDisplayCurrency persists the new preference via PATCH /profile', async () => {
    vi.mocked(profileApi.updateProfile).mockResolvedValue({
      name: 'Arthur',
      email: 'arthur@example.com',
      avatarUrl: null,
      accentColor: 'teal',
      preferredCurrency: 'USD',
    })
    const currencyStore = useCurrencyStore()
    await currencyStore.setDisplayCurrency('USD')
    expect(profileApi.updateProfile).toHaveBeenCalledWith({ preferredCurrency: 'USD' })
    expect(currencyStore.displayCurrency).toBe('USD')
  })

  it('load() reads the preferred currency from the profile endpoint', async () => {
    vi.mocked(profileApi.getProfile).mockResolvedValue({
      name: 'Arthur',
      email: 'arthur@example.com',
      avatarUrl: null,
      accentColor: 'teal',
      preferredCurrency: 'USD',
    })
    const currencyStore = useCurrencyStore()
    await currencyStore.load()
    expect(currencyStore.displayCurrency).toBe('USD')
    expect(currencyStore.loaded).toBe(true)
  })

  it('hydrate sets the display currency without calling the API', () => {
    const currencyStore = useCurrencyStore()
    currencyStore.hydrate('USD')
    expect(currencyStore.displayCurrency).toBe('USD')
    expect(currencyStore.loaded).toBe(true)
  })
})
