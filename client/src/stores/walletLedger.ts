import { defineStore } from 'pinia'
import { ref } from 'vue'
import { walletLedgerApi } from '@/api/walletLedger'
import type { WalletLedgerEntry } from '@/types'

const DEFAULT_PAGE_SIZE = 10

export const useWalletLedgerStore = defineStore('walletLedger', () => {
  const rows = ref<WalletLedgerEntry[]>([])
  const walletId = ref<string | null>(null)
  const page = ref(0)
  const pageSize = ref(DEFAULT_PAGE_SIZE)
  const totalElements = ref(0)
  const loading = ref(false)
  const loaded = ref(false)

  async function load(targetWalletId: string, pageNumber = 0) {
    loading.value = true
    walletId.value = targetWalletId
    page.value = pageNumber
    try {
      const result = await walletLedgerApi.findAll(targetWalletId, {
        page: pageNumber,
        size: pageSize.value,
      })
      rows.value = result.content
      totalElements.value = result.page.totalElements
    } finally {
      loading.value = false
      loaded.value = true
    }
  }

  async function refresh() {
    if (walletId.value) await load(walletId.value, page.value)
  }

  function reset() {
    rows.value = []
    walletId.value = null
    page.value = 0
    pageSize.value = DEFAULT_PAGE_SIZE
    totalElements.value = 0
    loading.value = false
    loaded.value = false
  }

  return {
    rows,
    walletId,
    page,
    pageSize,
    totalElements,
    loading,
    loaded,
    load,
    refresh,
    reset,
  }
})
