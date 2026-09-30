import { defineStore } from 'pinia'
import { ref } from 'vue'
import { walletMovesApi, type WalletMovePayload } from '@/api/walletMoves'
import type { WalletMoveRow } from '@/types'

export const useWalletMovesStore = defineStore('walletMoves', () => {
  const rows = ref<WalletMoveRow[]>([])
  const walletId = ref<string | null>(null)
  const page = ref(0)
  const pageSize = ref(10)
  const totalElements = ref(0)
  const totalPages = ref(0)
  const loading = ref(false)
  const loaded = ref(false)

  async function load(targetWalletId: string, pageNumber = 0) {
    loading.value = true
    walletId.value = targetWalletId
    page.value = pageNumber
    try {
      const result = await walletMovesApi.findAll(targetWalletId, {
        page: pageNumber,
        size: pageSize.value,
      })
      rows.value = result.content
      totalElements.value = result.page.totalElements
      totalPages.value = result.page.totalPages
    } finally {
      loading.value = false
      loaded.value = true
    }
  }

  async function move(originWalletId: string, payload: WalletMovePayload) {
    await walletMovesApi.move(originWalletId, payload)
    const touchesLoadedWallet =
      walletId.value === originWalletId || walletId.value === payload.destinationWalletId
    if (walletId.value && touchesLoadedWallet) {
      await load(walletId.value, 0)
    }
  }

  return {
    rows,
    walletId,
    page,
    pageSize,
    totalElements,
    totalPages,
    loading,
    loaded,
    load,
    move,
  }
})
