export type InvestmentSortKey =
  'asset' | 'wallet' | 'price' | 'invested' | 'current' | 'gain' | 'gainPct'
export type SortDirection = 'asc' | 'desc'
export interface InvestmentSort {
  key: InvestmentSortKey
  direction: SortDirection
}

export const VALID_SORT_KEYS: readonly InvestmentSortKey[] = [
  'asset',
  'wallet',
  'price',
  'invested',
  'current',
  'gain',
  'gainPct',
]
export const DEFAULT_SORT: Readonly<InvestmentSort> = { key: 'gainPct', direction: 'desc' }

export function nextSort(current: InvestmentSort, key: string): InvestmentSort | null {
  const nextKey = VALID_SORT_KEYS.find((sortKey) => sortKey === key)
  if (!nextKey) return null
  if (current.key !== nextKey) return { key: nextKey, direction: 'asc' }
  return { key: nextKey, direction: current.direction === 'asc' ? 'desc' : 'asc' }
}

export function sortParam(sort: InvestmentSort): string {
  return `${sort.key},${sort.direction}`
}
