import { useCurrencyStore } from '@/stores/currency'
import { useFormat } from '@/composables/useFormat'

export function useDisplayMoney() {
  const currencyStore = useCurrencyStore()
  const fmt = useFormat()

  function formatConverted(
    amount: number,
    fromCurrency: string,
    options: { compact?: boolean } = {},
  ): string {
    return fmt.money(
      currencyStore.convert(amount, fromCurrency),
      currencyStore.displayCurrency,
      options,
    )
  }

  return { formatConverted }
}
