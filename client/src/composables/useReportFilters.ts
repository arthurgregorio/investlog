import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { queryList } from '@/composables/useInvestmentFilters'
import { useWalletsStore } from '@/stores/wallets'
import { WALLET_TYPES } from '@/utils/walletTypes'
import type { WalletKind } from '@/types'

const VALID_KINDS: WalletKind[] = ['STOCKS', 'CRYPTO', 'FUNDS']

function listOrUndefined(values: string[]) {
  return values.length > 0 ? values : undefined
}

export function useReportFilters() {
  const route = useRoute()
  const walletsStore = useWalletsStore()

  function queryString(name: string) {
    const value = route.query[name]
    return typeof value === 'string' ? value : undefined
  }

  const kindFilter = computed<WalletKind | undefined>(() => {
    const filterParam = queryString('filter')
    return VALID_KINDS.find((kind) => kind === filterParam)
  })
  const walletIdFilter = computed(() => listOrUndefined(queryList(route.query, 'walletId')))
  const typeLabelFilter = computed(() => listOrUndefined(queryList(route.query, 'type')))
  const searchFilter = computed(() => queryString('search'))
  const hideFrozenFilter = computed(() => queryString('hideFrozen') === 'true')

  const activeFilterLabels = computed(() => {
    const labels: string[] = []
    if (kindFilter.value) labels.push(WALLET_TYPES[kindFilter.value].label)
    labels.push(...(typeLabelFilter.value ?? []))
    for (const walletId of walletIdFilter.value ?? []) {
      labels.push(walletsStore.walletById(walletId)?.name ?? 'Carteira selecionada')
    }
    if (searchFilter.value) labels.push(`"${searchFilter.value}"`)
    return labels
  })

  return {
    kindFilter,
    walletIdFilter,
    typeLabelFilter,
    searchFilter,
    hideFrozenFilter,
    activeFilterLabels,
  }
}
