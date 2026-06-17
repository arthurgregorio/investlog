import { defineStore } from 'pinia'
import { ref } from 'vue'
import { assetTypesApi } from '@/api/assetTypes'
import type { AssetType } from '@/types'

export const useTypesListStore = defineStore('typesList', () => {
  const stockTypes = ref<AssetType[]>([])
  const fundTypes = ref<AssetType[]>([])
  const loaded = ref(false)
  const loading = ref(false)

  async function load() {
    if (loaded.value) return
    loading.value = true
    try {
      const [stockTypesData, fundTypesData] = await Promise.all([
        assetTypesApi.findAllStockTypes(),
        assetTypesApi.findAllFundTypes(),
      ])
      stockTypes.value = stockTypesData
      fundTypes.value = fundTypesData
      loaded.value = true
    } finally {
      loading.value = false
    }
  }

  async function refresh() {
    loaded.value = false
    await load()
  }

  async function addStockType(name: string): Promise<AssetType> {
    const created = await assetTypesApi.createStockType(name)
    stockTypes.value = [...stockTypes.value, created]
    return created
  }

  async function removeStockType(id: string): Promise<void> {
    await assetTypesApi.removeStockType(id)
    stockTypes.value = stockTypes.value.filter((type) => type.id !== id)
  }

  async function addFundType(name: string): Promise<AssetType> {
    const created = await assetTypesApi.createFundType(name)
    fundTypes.value = [...fundTypes.value, created]
    return created
  }

  async function removeFundType(id: string): Promise<void> {
    await assetTypesApi.removeFundType(id)
    fundTypes.value = fundTypes.value.filter((type) => type.id !== id)
  }

  return {
    stockTypes,
    fundTypes,
    loaded,
    loading,
    load,
    refresh,
    addStockType,
    removeStockType,
    addFundType,
    removeFundType,
  }
})
