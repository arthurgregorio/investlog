import { defineStore } from 'pinia'
import { ref } from 'vue'
import { resultsApi } from '@/api/results'
import type { ResultRow, ResultSummary } from '@/types'

export const useResultsStore = defineStore('results', () => {
  const rows = ref<ResultRow[]>([])
  const page = ref(0)
  const pageSize = ref(20)
  const totalElements = ref(0)
  const totalPages = ref(0)
  const loading = ref(false)
  const loaded = ref(false)
  const summary = ref<ResultSummary | null>(null)
  const summaryLoading = ref(false)

  async function load(pageNumber = 0) {
    loading.value = true
    page.value = pageNumber
    try {
      const result = await resultsApi.findAll({ page: pageNumber, size: pageSize.value })
      rows.value = result.content
      totalElements.value = result.page.totalElements
      totalPages.value = result.page.totalPages
    } finally {
      loading.value = false
      loaded.value = true
    }
  }

  async function loadSummary() {
    summaryLoading.value = true
    try {
      summary.value = await resultsApi.getSummary()
    } finally {
      summaryLoading.value = false
    }
  }

  async function refresh() {
    await load(page.value)
  }

  return {
    rows,
    page,
    pageSize,
    totalElements,
    totalPages,
    loading,
    loaded,
    summary,
    summaryLoading,
    load,
    loadSummary,
    refresh,
  }
})
