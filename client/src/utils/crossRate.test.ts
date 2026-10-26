import { describe, expect, it } from 'vitest'
import { crossRate } from './crossRate'

const rates = [
  { currencyCode: 'BRL', rate: 1, isBase: true },
  { currencyCode: 'USD', rate: 5.18, isBase: false },
]

describe('crossRate', () => {
  it('is 1 between wallets in the same currency', () => {
    expect(crossRate(rates, 'USD', 'USD')).toBe(1)
  })

  it('gives how many destination units one origin unit buys', () => {
    expect(crossRate(rates, 'USD', 'BRL')).toBe(5.18)
    expect(crossRate(rates, 'BRL', 'USD')).toBe(0.19305)
  })

  it('treats a currency without a stored rate as the anchor', () => {
    expect(crossRate(rates, 'EUR', 'USD')).toBe(0.19305)
  })
})
