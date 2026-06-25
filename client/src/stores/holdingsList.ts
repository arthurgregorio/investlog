import { defineStore } from 'pinia'
import { ref } from 'vue'
import { holdingsApi } from '@/api/holdings'
import type { HoldingRow, WalletKind } from '@/types'

type KindFilter = 'all' | WalletKind

export interface HoldingsListOptions {
  typeLabel?: string
  walletId?: string
  search?: string
  sort?: string
}

export const useHoldingsListStore = defineStore('holdingsList', () => {
  const rows = ref<HoldingRow[]>([])
  const currentKind = ref<KindFilter>('all')
  const currentTypeLabel = ref<string | undefined>(undefined)
  const currentWalletId = ref<string | undefined>(undefined)
  const currentSearch = ref<string | undefined>(undefined)
  const currentSort = ref<string | undefined>(undefined)
  const page = ref(0)
  const pageSize = ref(20)
  const totalElements = ref(0)
  const totalPages = ref(0)
  const loading = ref(false)
  const loaded = ref(false)

  async function loadKind(kind: KindFilter, pageNumber = 0, options: HoldingsListOptions = {}) {
    loading.value = true
    currentKind.value = kind
    currentTypeLabel.value = options.typeLabel
    currentWalletId.value = options.walletId
    currentSearch.value = options.search
    currentSort.value = options.sort
    page.value = pageNumber
    try {
      const result = await holdingsApi.findAll({
        kind: kind === 'all' ? undefined : kind,
        typeLabel: options.typeLabel,
        walletId: options.walletId,
        search: options.search,
        sort: options.sort,
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

  async function refresh() {
    await loadKind(currentKind.value, page.value, {
      typeLabel: currentTypeLabel.value,
      walletId: currentWalletId.value,
      search: currentSearch.value,
      sort: currentSort.value,
    })
  }

  return {
    rows,
    currentKind,
    page,
    pageSize,
    totalElements,
    totalPages,
    loading,
    loaded,
    loadKind,
    refresh,
  }
})
