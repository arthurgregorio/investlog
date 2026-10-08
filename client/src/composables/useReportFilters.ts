import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useWalletsStore } from '@/stores/wallets'
import { WALLET_TYPES } from '@/utils/walletTypes'
import type { WalletKind } from '@/types'

const VALID_KINDS: WalletKind[] = ['STOCKS', 'CRYPTO', 'FUNDS']

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
  const walletIdFilter = computed(() => queryString('walletId'))
  const typeLabelFilter = computed(() => queryString('type'))
  const searchFilter = computed(() => queryString('search'))

  const activeFilterLabels = computed(() => {
    const labels: string[] = []
    if (kindFilter.value) labels.push(WALLET_TYPES[kindFilter.value].label)
    if (typeLabelFilter.value) labels.push(typeLabelFilter.value)
    if (walletIdFilter.value) {
      labels.push(walletsStore.walletById(walletIdFilter.value)?.name ?? 'Carteira selecionada')
    }
    if (searchFilter.value) labels.push(`"${searchFilter.value}"`)
    return labels
  })

  return { kindFilter, walletIdFilter, typeLabelFilter, searchFilter, activeFilterLabels }
}
