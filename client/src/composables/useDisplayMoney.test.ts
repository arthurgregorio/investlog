import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useDisplayMoney } from './useDisplayMoney'
import { fmt } from './useFormat'
import { useCurrencyStore } from '@/stores/currency'
import { useRatesStore } from '@/stores/rates'

describe('useDisplayMoney', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    useRatesStore().rates = [
      { currencyCode: 'BRL', rate: 1, isBase: true },
      { currencyCode: 'USD', rate: 5, isBase: false },
    ]
  })

  it('formats an amount already in the display currency without converting it', () => {
    useCurrencyStore().hydrate('BRL')
    const { formatConverted } = useDisplayMoney()

    expect(formatConverted(1234.5, 'BRL')).toBe(fmt.money(1234.5, 'BRL'))
  })

  it('converts into the display currency before formatting', () => {
    useCurrencyStore().hydrate('BRL')
    const { formatConverted } = useDisplayMoney()

    expect(formatConverted(20, 'USD')).toBe(fmt.money(100, 'BRL'))
  })

  it('passes the formatting options through', () => {
    useCurrencyStore().hydrate('USD')
    const { formatConverted } = useDisplayMoney()

    expect(formatConverted(25_000, 'BRL', { compact: true })).toBe(
      fmt.money(5_000, 'USD', { compact: true }),
    )
  })

  it('follows a display currency change', () => {
    const currencyStore = useCurrencyStore()
    currencyStore.hydrate('BRL')
    const { formatConverted } = useDisplayMoney()

    currencyStore.hydrate('USD')

    expect(formatConverted(100, 'BRL')).toBe(fmt.money(20, 'USD'))
  })
})
