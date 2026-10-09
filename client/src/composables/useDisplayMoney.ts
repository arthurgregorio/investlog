import { useCurrencyStore } from '@/stores/currency'
import { useFormat } from '@/composables/useFormat'

export interface DisplayAmount {
  amount: number
  currency: string
}

export function useDisplayMoney() {
  const currencyStore = useCurrencyStore()
  const fmt = useFormat()

  function toDisplayCurrency(amount: number, fromCurrency: string): DisplayAmount {
    return {
      amount: currencyStore.convert(amount, fromCurrency),
      currency: currencyStore.displayCurrency,
    }
  }

  function formatConverted(
    amount: number,
    fromCurrency: string,
    options: { compact?: boolean } = {},
  ): string {
    const converted = toDisplayCurrency(amount, fromCurrency)
    return fmt.money(converted.amount, converted.currency, options)
  }

  return { toDisplayCurrency, formatConverted }
}
