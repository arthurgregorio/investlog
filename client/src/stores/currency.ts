import { defineStore } from 'pinia'
import { ref } from 'vue'
import { profileApi } from '@/api/profile'
import { useRatesStore } from '@/stores/rates'

export const useCurrencyStore = defineStore('currency', () => {
  const ratesStore = useRatesStore()
  const displayCurrency = ref('BRL')
  const loaded = ref(false)
  const loading = ref(false)

  async function load() {
    if (loaded.value) return
    loading.value = true
    try {
      const profile = await profileApi.getProfile()
      displayCurrency.value = profile.preferredCurrency
      loaded.value = true
    } finally {
      loading.value = false
    }
  }

  function hydrate(currencyCode: string) {
    displayCurrency.value = currencyCode
    loaded.value = true
  }

  async function setDisplayCurrency(currencyCode: string) {
    if (currencyCode === displayCurrency.value) return
    const profile = await profileApi.updateProfile({ preferredCurrency: currencyCode })
    displayCurrency.value = profile.preferredCurrency
  }

  function rateToAnchor(currencyCode: string): number {
    if (currencyCode === ratesStore.baseCurrency) return 1
    return ratesStore.rates.find((rate) => rate.currencyCode === currencyCode)?.rate ?? 1
  }

  function convert(amount: number, fromCurrency: string): number {
    if (fromCurrency === displayCurrency.value) return amount
    return amount * (rateToAnchor(fromCurrency) / rateToAnchor(displayCurrency.value))
  }

  return { displayCurrency, loaded, loading, load, setDisplayCurrency, convert, hydrate }
})
