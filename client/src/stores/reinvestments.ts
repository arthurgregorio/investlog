import { defineStore } from 'pinia'
import { ref } from 'vue'
import { reinvestmentsApi, type ReinvestmentPayload } from '@/api/reinvestments'
import type { ReinvestmentRow } from '@/types'

export const useReinvestmentsStore = defineStore('reinvestments', () => {
  const rows = ref<ReinvestmentRow[]>([])
  const page = ref(0)
  const pageSize = ref(20)
  const totalElements = ref(0)
  const totalPages = ref(0)
  const loading = ref(false)
  const loaded = ref(false)

  async function load(pageNumber = 0, size = pageSize.value) {
    loading.value = true
    page.value = pageNumber
    pageSize.value = size
    try {
      const result = await reinvestmentsApi.findAll({ page: pageNumber, size })
      rows.value = result.content
      totalElements.value = result.page.totalElements
      totalPages.value = result.page.totalPages
    } finally {
      loading.value = false
      loaded.value = true
    }
  }

  async function reinvest(payload: ReinvestmentPayload) {
    await reinvestmentsApi.reinvest(payload)
    if (loaded.value) {
      await load(0, pageSize.value)
    }
  }

  return {
    rows,
    page,
    pageSize,
    totalElements,
    totalPages,
    loading,
    loaded,
    load,
    reinvest,
  }
})
