import { defineStore } from 'pinia'
import { ref } from 'vue'
import { overviewApi } from '@/api/overview'
import type { PortfolioSummary, SeriesPoint } from '@/types'

export const useOverviewStore = defineStore('overview', () => {
  const summary = ref<PortfolioSummary | null>(null)
  const series = ref<SeriesPoint[]>([])
  const loaded = ref(false)
  const loading = ref(false)

  async function load() {
    if (loaded.value) return
    loading.value = true
    try {
      const [summaryData, seriesData] = await Promise.all([
        overviewApi.getSummary(),
        overviewApi.getSeries(),
      ])
      summary.value = summaryData
      series.value = seriesData
      loaded.value = true
    } finally {
      loading.value = false
    }
  }

  async function refresh() {
    loaded.value = false
    await load()
  }

  return { summary, series, loaded, loading, load, refresh }
})
