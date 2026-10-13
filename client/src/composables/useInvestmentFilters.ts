import { onMounted, onScopeDispose, readonly, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { LocationQuery, LocationQueryRaw } from 'vue-router'
import type { WalletKind } from '@/types'

export type InvestmentKindFilter = 'all' | WalletKind
export type InvestmentSortKey =
  'asset' | 'wallet' | 'price' | 'invested' | 'current' | 'gain' | 'gainPct'
export type SortDirection = 'asc' | 'desc'
export interface InvestmentSort {
  key: InvestmentSortKey
  direction: SortDirection
}

export interface InvestmentsLoadRequest {
  kind: InvestmentKindFilter
  page: number
  typeLabel?: string[]
  walletId?: string[]
  search?: string
  sort?: string
  hideFrozen?: boolean
}

export interface InvestmentFiltersOptions {
  onReload: (request: InvestmentsLoadRequest) => void
  onFiltersChange?: () => void
}

const VALID_FILTERS: InvestmentKindFilter[] = ['all', 'STOCKS', 'CRYPTO', 'FUNDS']
const VALID_SORT_KEYS: InvestmentSortKey[] = [
  'asset',
  'wallet',
  'price',
  'invested',
  'current',
  'gain',
  'gainPct',
]
const DEFAULT_SORT: InvestmentSort = { key: 'gainPct', direction: 'desc' }
const DEFAULT_SORT_PARAM = `${DEFAULT_SORT.key},${DEFAULT_SORT.direction}`
const QUERY_KEYS = ['filter', 'walletId', 'type', 'search', 'hideFrozen', 'sort', 'page'] as const
const SEARCH_DEBOUNCE_MILLISECONDS = 300

function queryString(query: LocationQuery, key: string): string | undefined {
  const value = query[key]
  return typeof value === 'string' ? value : undefined
}

export function queryList(query: LocationQuery, key: string): string[] {
  const value = query[key]
  const values = Array.isArray(value) ? value : [value]
  return values.filter((item): item is string => typeof item === 'string' && item.length > 0)
}

export function parseFilter(query: LocationQuery): InvestmentKindFilter {
  const filterParam = queryString(query, 'filter')
  return VALID_FILTERS.find((filter) => filter === filterParam) ?? 'all'
}

export function parseSort(query: LocationQuery): InvestmentSort {
  const sortParam = queryString(query, 'sort')
  if (sortParam === undefined) return { ...DEFAULT_SORT }
  const [keyParam, directionParam] = sortParam.split(',')
  const key = VALID_SORT_KEYS.find((sortKey) => sortKey === keyParam)
  if (!key) return { ...DEFAULT_SORT }
  return { key, direction: directionParam === 'asc' ? 'asc' : 'desc' }
}

export function parseHideFrozen(query: LocationQuery): boolean {
  return queryString(query, 'hideFrozen') === 'true'
}

export function parsePage(query: LocationQuery): number {
  const pageParam = queryString(query, 'page')
  const pageNumber = pageParam === undefined ? NaN : parseInt(pageParam, 10)
  return Number.isFinite(pageNumber) && pageNumber > 1 ? pageNumber - 1 : 0
}

function fingerprintValue(value: unknown): string {
  if (Array.isArray(value)) return [...value].map(String).sort().join(',')
  return value == null ? '' : String(value)
}

export function queryFingerprint(query: LocationQuery | LocationQueryRaw): string {
  return QUERY_KEYS.map((key) => `${key}=${fingerprintValue(query[key])}`).join('&')
}

export function useInvestmentFilters(options: InvestmentFiltersOptions) {
  const route = useRoute()
  const router = useRouter()

  const initialSort = parseSort(route.query)
  const activeFilter = ref<InvestmentKindFilter>(parseFilter(route.query))
  const typeLabelFilter = ref<string[]>(queryList(route.query, 'type'))
  const walletIdFilter = ref<string[]>(queryList(route.query, 'walletId'))
  const searchQuery = ref(queryString(route.query, 'search') ?? '')
  const hideFrozenFilter = ref(parseHideFrozen(route.query))
  const sortKey = ref<InvestmentSortKey>(initialSort.key)
  const sortDirection = ref<SortDirection>(initialSort.direction)

  let lastWrittenQueryFingerprint = queryFingerprint(route.query)
  let searchDebounceHandle: ReturnType<typeof setTimeout> | undefined

  function trimmedSearch() {
    return searchQuery.value.trim() || undefined
  }

  function sortParam() {
    return `${sortKey.value},${sortDirection.value}`
  }

  function listOrUndefined(values: string[]) {
    return values.length > 0 ? [...values] : undefined
  }

  function reportQuery(): LocationQueryRaw {
    const query: LocationQueryRaw = {}
    if (activeFilter.value !== 'all') query.filter = activeFilter.value
    if (walletIdFilter.value.length > 0) query.walletId = [...walletIdFilter.value]
    if (typeLabelFilter.value.length > 0) query.type = [...typeLabelFilter.value]
    const search = trimmedSearch()
    if (search) query.search = search
    if (hideFrozenFilter.value) query.hideFrozen = 'true'
    return query
  }

  function buildQuery(pageNumber: number): LocationQueryRaw {
    const query = reportQuery()
    const sort = sortParam()
    if (sort !== DEFAULT_SORT_PARAM) query.sort = sort
    if (pageNumber > 0) query.page = String(pageNumber + 1)
    return query
  }

  function reload(pageNumber = 0) {
    const query = buildQuery(pageNumber)
    lastWrittenQueryFingerprint = queryFingerprint(query)
    router.replace({ query })
    options.onReload({
      kind: activeFilter.value,
      page: pageNumber,
      typeLabel: listOrUndefined(typeLabelFilter.value),
      walletId: listOrUndefined(walletIdFilter.value),
      search: trimmedSearch(),
      sort: sortParam(),
      hideFrozen: hideFrozenFilter.value || undefined,
    })
  }

  function reloadWithNewFilters() {
    options.onFiltersChange?.()
    reload(0)
  }

  function hydrateFromRoute(query: LocationQuery) {
    activeFilter.value = parseFilter(query)
    walletIdFilter.value = queryList(query, 'walletId')
    typeLabelFilter.value = queryList(query, 'type')
    searchQuery.value = queryString(query, 'search') ?? ''
    hideFrozenFilter.value = parseHideFrozen(query)
    const sort = parseSort(query)
    sortKey.value = sort.key
    sortDirection.value = sort.direction
  }

  function selectTab(filter: InvestmentKindFilter, compatibleWalletIds: readonly string[] = []) {
    if (filter === activeFilter.value) return
    activeFilter.value = filter
    walletIdFilter.value = walletIdFilter.value.filter((walletId) =>
      compatibleWalletIds.includes(walletId),
    )
    typeLabelFilter.value = []
    reloadWithNewFilters()
  }

  function toggle(values: string[], value: string) {
    return values.includes(value) ? values.filter((item) => item !== value) : [...values, value]
  }

  function toggleWallet(walletId: string) {
    walletIdFilter.value = toggle(walletIdFilter.value, walletId)
    reloadWithNewFilters()
  }

  function toggleType(typeLabel: string) {
    typeLabelFilter.value = toggle(typeLabelFilter.value, typeLabel)
    reloadWithNewFilters()
  }

  function changeHideFrozen(hideFrozen: boolean) {
    hideFrozenFilter.value = hideFrozen
    reloadWithNewFilters()
  }

  function cancelPendingSearch() {
    if (searchDebounceHandle) clearTimeout(searchDebounceHandle)
  }

  function changeSearch(search: string) {
    searchQuery.value = search
    cancelPendingSearch()
    searchDebounceHandle = setTimeout(reloadWithNewFilters, SEARCH_DEBOUNCE_MILLISECONDS)
  }

  function clearFilters() {
    cancelPendingSearch()
    activeFilter.value = 'all'
    walletIdFilter.value = []
    typeLabelFilter.value = []
    searchQuery.value = ''
    hideFrozenFilter.value = false
    reloadWithNewFilters()
  }

  function toggleSort(key: string) {
    const nextKey = VALID_SORT_KEYS.find((sortKey) => sortKey === key)
    if (!nextKey) return
    if (sortKey.value === nextKey) {
      sortDirection.value = sortDirection.value === 'asc' ? 'desc' : 'asc'
    } else {
      sortKey.value = nextKey
      sortDirection.value = 'asc'
    }
    reload(0)
  }

  watch(
    () => route.query,
    (newQuery) => {
      if (queryFingerprint(newQuery) === lastWrittenQueryFingerprint) return
      hydrateFromRoute(newQuery)
      options.onFiltersChange?.()
      reload(parsePage(newQuery))
    },
  )

  onMounted(() => reload(parsePage(route.query)))
  onScopeDispose(cancelPendingSearch)

  return {
    activeFilter: readonly(activeFilter),
    typeLabelFilter: readonly(typeLabelFilter),
    walletIdFilter: readonly(walletIdFilter),
    searchQuery: readonly(searchQuery),
    hideFrozenFilter: readonly(hideFrozenFilter),
    sortKey: readonly(sortKey),
    sortDirection: readonly(sortDirection),
    selectTab,
    toggleWallet,
    toggleType,
    changeSearch,
    changeHideFrozen,
    clearFilters,
    toggleSort,
    reload,
    reportQuery,
  }
}
