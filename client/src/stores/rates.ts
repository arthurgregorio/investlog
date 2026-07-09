import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { ratesApi } from '@/api/rates'
import type { CurrencyRate } from '@/types'

export const useRatesStore = defineStore('rates', () => {
  const rates = ref<CurrencyRate[]>([])
  const loaded = ref(false)
  const loading = ref(false)

  const baseCurrency = computed(
    () => rates.value.find((rate) => rate.isBase)?.currencyCode ?? 'BRL',
  )
  const currencyCodes = computed(() => rates.value.map((rate) => rate.currencyCode))

  async function load() {
    if (loaded.value) return
    loading.value = true
    try {
      rates.value = await ratesApi.findAll()
      loaded.value = true
    } finally {
      loading.value = false
    }
  }

  async function refresh() {
    loaded.value = false
    await load()
  }

  async function upsertRate(currencyCode: string, rate: number, isBase: boolean): Promise<void> {
    const updated = await ratesApi.upsert(currencyCode, rate, isBase)
    const index = rates.value.findIndex((r) => r.currencyCode === currencyCode)
    if (index >= 0) {
      rates.value = rates.value.map((r) => (r.currencyCode === currencyCode ? updated : r))
    } else {
      rates.value = [...rates.value, updated]
    }
    if (isBase) {
      rates.value = rates.value.map((r) =>
        r.currencyCode !== currencyCode ? { ...r, isBase: false } : r,
      )
    }
  }

  return { rates, loaded, loading, baseCurrency, currencyCodes, load, refresh, upsertRate }
})
