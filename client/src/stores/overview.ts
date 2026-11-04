import { defineStore } from 'pinia'
import { ref } from 'vue'
import { overviewApi } from '@/api/overview'
import type { MonthSummary, PortfolioSummary, SeriesPoint } from '@/types'

export const useOverviewStore = defineStore('overview', () => {
  const summary = ref<PortfolioSummary | null>(null)
  const series = ref<SeriesPoint[]>([])
  const month = ref<MonthSummary | null>(null)
  const loaded = ref(false)
  const loading = ref(false)

  async function load() {
    if (loaded.value) return
    loading.value = true
    try {
      const [summaryData, seriesData, monthData] = await Promise.all([
        overviewApi.getSummary(),
        overviewApi.getSeries(),
        overviewApi.getMonth(),
      ])
      summary.value = summaryData
      series.value = seriesData
      month.value = monthData
      loaded.value = true
    } finally {
      loading.value = false
    }
  }

  async function refresh() {
    loaded.value = false
    await load()
  }

  function reset() {
    summary.value = null
    series.value = []
    month.value = null
    loaded.value = false
    loading.value = false
  }

  return { summary, series, month, loaded, loading, load, refresh, reset }
})
