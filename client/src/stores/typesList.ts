import { defineStore } from 'pinia'
import { ref } from 'vue'
import { assetTypesApi } from '@/api/assetTypes'
import type { AssetType } from '@/types'

export const useTypesListStore = defineStore('typesList', () => {
  const stockTypes = ref<AssetType[]>([])
  const fundTypes = ref<AssetType[]>([])
  const stockSegments = ref<AssetType[]>([])
  const loaded = ref(false)
  const loading = ref(false)

  async function load() {
    if (loaded.value) return
    loading.value = true
    try {
      const [stockTypesData, fundTypesData, stockSegmentsData] = await Promise.all([
        assetTypesApi.findAllStockTypes(),
        assetTypesApi.findAllFundTypes(),
        assetTypesApi.findAllStockSegments(),
      ])
      stockTypes.value = stockTypesData
      fundTypes.value = fundTypesData
      stockSegments.value = stockSegmentsData
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

  async function updateStockType(id: string, name: string): Promise<AssetType> {
    const updated = await assetTypesApi.updateStockType(id, name)
    stockTypes.value = stockTypes.value.map((type) => (type.id === id ? updated : type))
    return updated
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

  async function updateFundType(id: string, name: string): Promise<AssetType> {
    const updated = await assetTypesApi.updateFundType(id, name)
    fundTypes.value = fundTypes.value.map((type) => (type.id === id ? updated : type))
    return updated
  }

  async function removeFundType(id: string): Promise<void> {
    await assetTypesApi.removeFundType(id)
    fundTypes.value = fundTypes.value.filter((type) => type.id !== id)
  }

  async function addStockSegment(name: string): Promise<AssetType> {
    const created = await assetTypesApi.createStockSegment(name)
    stockSegments.value = [...stockSegments.value, created]
    return created
  }

  async function updateStockSegment(id: string, name: string): Promise<AssetType> {
    const updated = await assetTypesApi.updateStockSegment(id, name)
    stockSegments.value = stockSegments.value.map((segment) =>
      segment.id === id ? updated : segment,
    )
    return updated
  }

  async function removeStockSegment(id: string): Promise<void> {
    await assetTypesApi.removeStockSegment(id)
    stockSegments.value = stockSegments.value.filter((segment) => segment.id !== id)
  }

  return {
    stockTypes,
    fundTypes,
    stockSegments,
    loaded,
    loading,
    load,
    refresh,
    addStockType,
    updateStockType,
    removeStockType,
    addFundType,
    updateFundType,
    removeFundType,
    addStockSegment,
    updateStockSegment,
    removeStockSegment,
  }
})
