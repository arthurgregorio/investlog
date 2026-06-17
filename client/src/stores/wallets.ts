import { defineStore } from 'pinia'
import { ref } from 'vue'
import { walletsApi } from '@/api/wallets'
import type { WalletResponse } from '@/types'

export const useWalletsStore = defineStore('wallets', () => {
  const wallets = ref<WalletResponse[]>([])
  const loaded = ref(false)
  const loading = ref(false)

  async function load() {
    if (loaded.value) return
    loading.value = true
    try {
      const page = await walletsApi.findAll()
      wallets.value = page.content
      loaded.value = true
    } finally {
      loading.value = false
    }
  }

  async function refresh() {
    loaded.value = false
    await load()
  }

  function walletById(id: string): WalletResponse | undefined {
    return wallets.value.find((wallet) => wallet.id === id)
  }

  return { wallets, loaded, loading, load, refresh, walletById }
})
