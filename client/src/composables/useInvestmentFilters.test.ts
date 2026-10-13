import { afterEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import {
  parseFilter,
  parseHideFrozen,
  parsePage,
  parseSort,
  queryFingerprint,
  queryList,
  useInvestmentFilters,
  type InvestmentsLoadRequest,
} from './useInvestmentFilters'

let activeWrapper: VueWrapper | undefined

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

async function mountFilters(query = '') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/investments', component: { render: () => null } }],
  })
  router.push(`/investments${query}`)
  await router.isReady()

  const onReload = vi.fn<(request: InvestmentsLoadRequest) => void>()
  const onFiltersChange = vi.fn()
  let filters!: ReturnType<typeof useInvestmentFilters>
  activeWrapper = mount(
    defineComponent({
      setup() {
        filters = useInvestmentFilters({ onReload, onFiltersChange })
        return () => h('div')
      },
    }),
    { global: { plugins: [router] } },
  )
  await flushPromises()
  return { filters, router, onReload, onFiltersChange }
}

afterEach(() => {
  vi.useRealTimers()
  activeWrapper?.unmount()
  activeWrapper = undefined
})

describe('query parsing', () => {
  it('accepts the known kinds and falls back to all', () => {
    expect(parseFilter({ filter: 'CRYPTO' })).toBe('CRYPTO')
    expect(parseFilter({ filter: 'BONDS' })).toBe('all')
    expect(parseFilter({ filter: ['STOCKS'] })).toBe('all')
    expect(parseFilter({})).toBe('all')
  })

  it('reads a known sort key with its direction, defaulting to descending', () => {
    expect(parseSort({ sort: 'gain,asc' })).toEqual({ key: 'gain', direction: 'asc' })
    expect(parseSort({ sort: 'price,sideways' })).toEqual({ key: 'price', direction: 'desc' })
    expect(parseSort({ sort: 'price' })).toEqual({ key: 'price', direction: 'desc' })
  })

  it('reads the gain percentage and asset sort keys', () => {
    expect(parseSort({ sort: 'gainPct,asc' })).toEqual({ key: 'gainPct', direction: 'asc' })
    expect(parseSort({ sort: 'asset,desc' })).toEqual({ key: 'asset', direction: 'desc' })
  })

  it('falls back to the gain percentage descending for an unknown or missing sort key', () => {
    expect(parseSort({ sort: 'ticker,asc' })).toEqual({ key: 'gainPct', direction: 'desc' })
    expect(parseSort({})).toEqual({ key: 'gainPct', direction: 'desc' })
  })

  it('turns the one-based page into a zero-based one, ignoring anything below two', () => {
    expect(parsePage({ page: '3' })).toBe(2)
    expect(parsePage({ page: '1' })).toBe(0)
    expect(parsePage({ page: '0' })).toBe(0)
    expect(parsePage({ page: 'abc' })).toBe(0)
    expect(parsePage({})).toBe(0)
  })

  it('hides frozen holdings only for an explicit true', () => {
    expect(parseHideFrozen({ hideFrozen: 'true' })).toBe(true)
    expect(parseHideFrozen({ hideFrozen: 'false' })).toBe(false)
    expect(parseHideFrozen({ hideFrozen: '1' })).toBe(false)
    expect(parseHideFrozen({ hideFrozen: ['true'] })).toBe(false)
    expect(parseHideFrozen({})).toBe(false)
  })

  it('fingerprints only the page query keys, in a fixed order', () => {
    expect(queryFingerprint({ page: '2', filter: 'FUNDS', unrelated: 'x' })).toBe(
      'filter=FUNDS&walletId=&type=&search=&hideFrozen=&sort=&page=2',
    )
  })

  it('fingerprints a list the same whatever its order', () => {
    expect(queryFingerprint({ walletId: ['b', 'a'] })).toBe(
      queryFingerprint({ walletId: ['a', 'b'] }),
    )
  })

  it('reads a single value or repeated values as a list, skipping empty ones', () => {
    expect(queryList({ walletId: 'wallet-1' }, 'walletId')).toEqual(['wallet-1'])
    expect(queryList({ walletId: ['wallet-1', null, '', 'wallet-2'] }, 'walletId')).toEqual([
      'wallet-1',
      'wallet-2',
    ])
    expect(queryList({}, 'walletId')).toEqual([])
  })
})

describe('useInvestmentFilters', () => {
  it('hydrates its state from the URL and loads that page when mounted', async () => {
    const { filters, onReload, onFiltersChange } = await mountFilters(
      '?filter=STOCKS&walletId=wallet-1&type=FII&search=petr&sort=gain,asc&page=2',
    )

    expect(filters.activeFilter.value).toBe('STOCKS')
    expect(filters.walletIdFilter.value).toEqual(['wallet-1'])
    expect(filters.typeLabelFilter.value).toEqual(['FII'])
    expect(filters.searchQuery.value).toBe('petr')
    expect(filters.sortKey.value).toBe('gain')
    expect(filters.sortDirection.value).toBe('asc')
    expect(onReload).toHaveBeenCalledExactlyOnceWith({
      kind: 'STOCKS',
      page: 1,
      walletId: ['wallet-1'],
      typeLabel: ['FII'],
      search: 'petr',
      sort: 'gain,asc',
    })
    expect(onFiltersChange).not.toHaveBeenCalled()
  })

  it('loads by gain percentage descending when the URL has no sort, without writing it', async () => {
    const { filters, router, onReload } = await mountFilters()

    expect(filters.sortKey.value).toBe('gainPct')
    expect(filters.sortDirection.value).toBe('desc')
    expect(onReload).toHaveBeenCalledExactlyOnceWith(
      expect.objectContaining({ page: 0, sort: 'gainPct,desc' }),
    )
    expect(router.currentRoute.value.query).toEqual({})
  })

  it('replaces an unknown sort key in the URL with the default', async () => {
    const { router, onReload } = await mountFilters('?sort=colour,asc')

    expect(onReload).toHaveBeenCalledExactlyOnceWith(
      expect.objectContaining({ sort: 'gainPct,desc' }),
    )
    expect(router.currentRoute.value.query).toEqual({})
  })

  it('writes a flip of the default column to the URL and drops it again on the way back', async () => {
    const { filters, router, onReload } = await mountFilters()
    onReload.mockClear()

    filters.toggleSort('gainPct')
    await flushPromises()
    expect(router.currentRoute.value.query).toEqual({ sort: 'gainPct,asc' })

    filters.toggleSort('gainPct')
    await flushPromises()
    expect(router.currentRoute.value.query).toEqual({})
    expect(onReload.mock.calls.map(([request]) => request.sort)).toEqual([
      'gainPct,asc',
      'gainPct,desc',
    ])
  })

  it('writes a class change to the URL, keeping the search, without reloading for its own echo', async () => {
    const { filters, router, onReload, onFiltersChange } = await mountFilters(
      '?walletId=wallet-1&search=petr&sort=price,desc&page=3',
    )
    onReload.mockClear()

    filters.selectTab('CRYPTO')
    await flushPromises()

    expect(router.currentRoute.value.query).toEqual({
      filter: 'CRYPTO',
      search: 'petr',
      sort: 'price,desc',
    })
    expect(onFiltersChange).toHaveBeenCalledTimes(1)
    expect(onReload).toHaveBeenCalledExactlyOnceWith({
      kind: 'CRYPTO',
      page: 0,
      walletId: undefined,
      typeLabel: undefined,
      search: 'petr',
      sort: 'price,desc',
    })
  })

  it('keeps the wallets that still belong to the new class and always drops the types', async () => {
    const { filters, router } = await mountFilters(
      '?filter=STOCKS&walletId=wallet-1&walletId=wallet-2&type=FII',
    )

    filters.selectTab('all', ['wallet-1', 'wallet-9'])
    await flushPromises()

    expect(filters.walletIdFilter.value).toEqual(['wallet-1'])
    expect(filters.typeLabelFilter.value).toEqual([])
    expect(router.currentRoute.value.query).toEqual({ walletId: ['wallet-1'] })
  })

  it('ignores a click on the active tab', async () => {
    const { filters, onReload } = await mountFilters('?filter=FUNDS')
    onReload.mockClear()

    filters.selectTab('FUNDS')

    expect(onReload).not.toHaveBeenCalled()
  })

  it('re-hydrates and reloads on a navigation it did not write', async () => {
    const { filters, router, onReload, onFiltersChange } = await mountFilters('?filter=STOCKS')
    onReload.mockClear()

    await router.push('/investments?filter=FUNDS&type=Renda%20Fixa&page=2')
    await flushPromises()

    expect(filters.activeFilter.value).toBe('FUNDS')
    expect(filters.typeLabelFilter.value).toEqual(['Renda Fixa'])
    expect(filters.sortKey.value).toBe('gainPct')
    expect(filters.sortDirection.value).toBe('desc')
    expect(onFiltersChange).toHaveBeenCalledTimes(1)
    expect(onReload).toHaveBeenCalledExactlyOnceWith(
      expect.objectContaining({ kind: 'FUNDS', page: 1, typeLabel: ['Renda Fixa'] }),
    )
    expect(router.currentRoute.value.query).toEqual({
      filter: 'FUNDS',
      type: 'Renda Fixa',
      page: '2',
    })
  })

  it('toggles several wallets and types on and off, going back to the first page', async () => {
    const { filters, router, onReload } = await mountFilters('?filter=STOCKS&page=4')
    onReload.mockClear()

    filters.toggleWallet('wallet-1')
    filters.toggleWallet('wallet-2')
    filters.toggleType('FII')
    filters.toggleType('ETF')
    await flushPromises()
    expect(router.currentRoute.value.query).toEqual({
      filter: 'STOCKS',
      walletId: ['wallet-1', 'wallet-2'],
      type: ['FII', 'ETF'],
    })
    expect(onReload.mock.lastCall?.[0]).toEqual(
      expect.objectContaining({ walletId: ['wallet-1', 'wallet-2'], typeLabel: ['FII', 'ETF'] }),
    )

    filters.toggleWallet('wallet-1')
    filters.toggleType('FII')
    filters.toggleType('ETF')
    await flushPromises()
    expect(router.currentRoute.value.query).toEqual({ filter: 'STOCKS', walletId: ['wallet-2'] })
    expect(onReload.mock.lastCall?.[0].typeLabel).toBeUndefined()
    expect(onReload.mock.calls.map(([request]) => request.page)).toEqual([0, 0, 0, 0, 0, 0, 0])
  })

  it('reads repeated wallets and types from the URL', async () => {
    const { filters, onReload } = await mountFilters(
      '?filter=FUNDS&walletId=wallet-1&walletId=wallet-2&type=Ações&type=Multimercado',
    )

    expect(filters.walletIdFilter.value).toEqual(['wallet-1', 'wallet-2'])
    expect(filters.typeLabelFilter.value).toEqual(['Ações', 'Multimercado'])
    expect(onReload).toHaveBeenCalledExactlyOnceWith(
      expect.objectContaining({
        walletId: ['wallet-1', 'wallet-2'],
        typeLabel: ['Ações', 'Multimercado'],
      }),
    )
  })

  it('does not reload for its own echo of a list in a different order', async () => {
    const { filters, router, onReload } = await mountFilters()
    filters.toggleWallet('wallet-2')
    filters.toggleWallet('wallet-1')
    await flushPromises()
    onReload.mockClear()

    await router.replace({ query: { walletId: ['wallet-1', 'wallet-2'] } })
    await flushPromises()

    expect(onReload).not.toHaveBeenCalled()
  })

  it('clears every filter but the sort and reloads from the first page', async () => {
    const { filters, router, onReload, onFiltersChange } = await mountFilters(
      '?filter=STOCKS&walletId=wallet-1&type=FII&search=petr&hideFrozen=true&sort=price,asc&page=3',
    )
    onReload.mockClear()
    vi.useFakeTimers()
    filters.changeSearch('vale')

    filters.clearFilters()
    vi.advanceTimersByTime(300)
    vi.useRealTimers()
    await flushPromises()

    expect(filters.activeFilter.value).toBe('all')
    expect(filters.walletIdFilter.value).toEqual([])
    expect(filters.typeLabelFilter.value).toEqual([])
    expect(filters.searchQuery.value).toBe('')
    expect(filters.hideFrozenFilter.value).toBe(false)
    expect(router.currentRoute.value.query).toEqual({ sort: 'price,asc' })
    expect(onFiltersChange).toHaveBeenCalledTimes(1)
    expect(onReload).toHaveBeenCalledExactlyOnceWith(
      expect.objectContaining({ kind: 'all', page: 0, search: undefined, sort: 'price,asc' }),
    )
  })

  it('waits 300 ms after the last keystroke before reloading with the trimmed term', async () => {
    const { filters, onReload, onFiltersChange } = await mountFilters()
    onReload.mockClear()
    vi.useFakeTimers()

    filters.changeSearch('pe')
    vi.advanceTimersByTime(200)
    filters.changeSearch('  petr  ')
    vi.advanceTimersByTime(299)
    expect(filters.searchQuery.value).toBe('  petr  ')
    expect(onReload).not.toHaveBeenCalled()

    vi.advanceTimersByTime(1)
    expect(onFiltersChange).toHaveBeenCalledTimes(1)
    expect(onReload).toHaveBeenCalledExactlyOnceWith(
      expect.objectContaining({ page: 0, search: 'petr' }),
    )
  })

  it('drops a pending search when the component unmounts', async () => {
    const { filters, onReload } = await mountFilters()
    onReload.mockClear()
    vi.useFakeTimers()

    filters.changeSearch('petr')
    activeWrapper?.unmount()
    activeWrapper = undefined
    vi.advanceTimersByTime(300)

    expect(onReload).not.toHaveBeenCalled()
  })

  it('sorts ascending first, flips on the same key and ignores unknown keys', async () => {
    const { filters, router, onReload, onFiltersChange } = await mountFilters('?page=2')
    onReload.mockClear()

    filters.toggleSort('gain')
    expect(filters.sortDirection.value).toBe('asc')
    filters.toggleSort('gain')
    expect(filters.sortDirection.value).toBe('desc')
    filters.toggleSort('ticker')
    await flushPromises()

    expect(filters.sortKey.value).toBe('gain')
    expect(router.currentRoute.value.query).toEqual({ sort: 'gain,desc' })
    expect(onReload).toHaveBeenCalledTimes(2)
    expect(onFiltersChange).not.toHaveBeenCalled()
  })

  it('reloads a given page keeping the filters and writes it one-based', async () => {
    const { filters, router, onReload } = await mountFilters('?filter=CRYPTO')
    onReload.mockClear()

    filters.reload(2)
    await flushPromises()

    expect(router.currentRoute.value.query).toEqual({ filter: 'CRYPTO', page: '3' })
    expect(onReload).toHaveBeenCalledExactlyOnceWith(
      expect.objectContaining({ kind: 'CRYPTO', page: 2 }),
    )
  })

  it('hides frozen holdings from the first page, keeping the flag in the URL only while it is on', async () => {
    const { filters, router, onReload, onFiltersChange } =
      await mountFilters('?filter=STOCKS&page=3')
    expect(filters.hideFrozenFilter.value).toBe(false)
    onReload.mockClear()

    filters.changeHideFrozen(true)
    await flushPromises()

    expect(filters.hideFrozenFilter.value).toBe(true)
    expect(router.currentRoute.value.query).toEqual({ filter: 'STOCKS', hideFrozen: 'true' })
    expect(onFiltersChange).toHaveBeenCalledTimes(1)
    expect(onReload).toHaveBeenCalledExactlyOnceWith(
      expect.objectContaining({ kind: 'STOCKS', page: 0, hideFrozen: true }),
    )

    filters.changeHideFrozen(false)
    await flushPromises()

    expect(router.currentRoute.value.query).toEqual({ filter: 'STOCKS' })
    expect(onReload).toHaveBeenCalledTimes(2)
    expect(onReload.mock.lastCall?.[0].hideFrozen).toBeUndefined()
  })

  it('reads the hide-frozen flag from the URL and keeps it across a tab change', async () => {
    const { filters, router, onReload } = await mountFilters('?hideFrozen=true')

    expect(filters.hideFrozenFilter.value).toBe(true)
    expect(onReload).toHaveBeenCalledExactlyOnceWith(
      expect.objectContaining({ kind: 'all', page: 0, hideFrozen: true }),
    )
    onReload.mockClear()

    filters.selectTab('CRYPTO')
    await flushPromises()

    expect(router.currentRoute.value.query).toEqual({ filter: 'CRYPTO', hideFrozen: 'true' })
    expect(onReload).toHaveBeenCalledExactlyOnceWith(
      expect.objectContaining({ kind: 'CRYPTO', hideFrozen: true }),
    )
  })

  it('drops the hide-frozen flag on a navigation without it', async () => {
    const { filters, router, onReload } = await mountFilters('?hideFrozen=true')
    onReload.mockClear()

    await router.push('/investments')
    await flushPromises()

    expect(filters.hideFrozenFilter.value).toBe(false)
    expect(onReload).toHaveBeenCalledTimes(1)
    expect(onReload.mock.lastCall?.[0].hideFrozen).toBeUndefined()
  })

  it('carries the hide-frozen flag into the report query', async () => {
    const { filters } = await mountFilters('?filter=FUNDS&hideFrozen=true&page=2')

    expect(filters.reportQuery()).toEqual({ filter: 'FUNDS', hideFrozen: 'true' })
  })

  it('builds the report query from the filters, without sort or page', async () => {
    const { filters } = await mountFilters(
      '?filter=STOCKS&walletId=wallet-1&type=FII&search=%20petr%20&sort=gain,asc&page=2',
    )

    expect(filters.reportQuery()).toEqual({
      filter: 'STOCKS',
      walletId: ['wallet-1'],
      type: ['FII'],
      search: 'petr',
    })
  })
})
