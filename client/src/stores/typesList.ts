import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { assetTypesApi } from '@/api/assetTypes'
import type { AssetType, HoldingType, WalletKind } from '@/types'

export const useTypesListStore = defineStore('typesList', () => {
  const holdingTypes = ref<HoldingType[]>([])
  const segments = ref<AssetType[]>([])
  const loaded = ref(false)
  const loading = ref(false)

  const typesForKind = computed(
    () => (kind: WalletKind) =>
      holdingTypes.value.filter((holdingType) => holdingType.kinds.includes(kind)),
  )

  async function load() {
    if (loaded.value) return
    loading.value = true
    try {
      const [holdingTypesData, segmentsData] = await Promise.all([
        assetTypesApi.findAllHoldingTypes(),
        assetTypesApi.findAllSegments(),
      ])
      holdingTypes.value = holdingTypesData
      segments.value = segmentsData
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
    holdingTypes.value = []
    segments.value = []
    loaded.value = false
    loading.value = false
  }

  async function addHoldingType(name: string, kinds: WalletKind[]): Promise<HoldingType> {
    const created = await assetTypesApi.createHoldingType(name, kinds)
    holdingTypes.value = [...holdingTypes.value, created]
    return created
  }

  async function updateHoldingType(
    id: string,
    name: string,
    kinds: WalletKind[],
  ): Promise<HoldingType> {
    const updated = await assetTypesApi.updateHoldingType(id, name, kinds)
    holdingTypes.value = holdingTypes.value.map((holdingType) =>
      holdingType.id === id ? updated : holdingType,
    )
    return updated
  }

  async function removeHoldingType(id: string): Promise<void> {
    await assetTypesApi.removeHoldingType(id)
    holdingTypes.value = holdingTypes.value.filter((holdingType) => holdingType.id !== id)
  }

  async function addSegment(name: string): Promise<AssetType> {
    const created = await assetTypesApi.createSegment(name)
    segments.value = [...segments.value, created]
    return created
  }

  async function updateSegment(id: string, name: string): Promise<AssetType> {
    const updated = await assetTypesApi.updateSegment(id, name)
    segments.value = segments.value.map((segment) => (segment.id === id ? updated : segment))
    return updated
  }

  async function removeSegment(id: string): Promise<void> {
    await assetTypesApi.removeSegment(id)
    segments.value = segments.value.filter((segment) => segment.id !== id)
  }

  return {
    holdingTypes,
    segments,
    typesForKind,
    loaded,
    loading,
    load,
    refresh,
    reset,
    addHoldingType,
    updateHoldingType,
    removeHoldingType,
    addSegment,
    updateSegment,
    removeSegment,
  }
})
