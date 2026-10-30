import type { CurrencyRate } from '@/types'

const RATE_DECIMALS = 6

export function crossRate(rates: CurrencyRate[], fromCurrency: string, toCurrency: string): number {
  if (fromCurrency === toCurrency) return 1
  const rateOf = (currencyCode: string) =>
    rates.find((rate) => rate.currencyCode === currencyCode)?.rate ?? 1
  return Number((rateOf(fromCurrency) / rateOf(toCurrency)).toFixed(RATE_DECIMALS))
}
