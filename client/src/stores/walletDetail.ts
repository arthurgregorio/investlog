import { defineStore } from 'pinia'
import { ref } from 'vue'
import { walletDetailApi } from '@/api/walletDetail'
import type { WalletDetail } from '@/types'

export const useWalletDetailStore = defineStore('walletDetail', () => {
  const detail = ref<WalletDetail | null>(null)
  const loading = ref(false)
  const loaded = ref(false)

  async function load(walletId: string) {
    loading.value = true
    try {
      detail.value = await walletDetailApi.get(walletId)
    } finally {
      loading.value = false
      loaded.value = true
    }
  }

  async function refresh() {
    if (detail.value) {
      await load(detail.value.id)
    }
  }

  function reset() {
    detail.value = null
    loaded.value = false
  }

  return { detail, loading, loaded, load, refresh, reset }
})
