import { afterEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import {
  parseFilter,
  parsePage,
  parseSort,
  queryFingerprint,
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

  it('drops an unknown or missing sort key', () => {
    expect(parseSort({ sort: 'ticker,asc' })).toEqual({ key: null, direction: 'desc' })
    expect(parseSort({})).toEqual({ key: null, direction: 'desc' })
  })

  it('turns the one-based page into a zero-based one, ignoring anything below two', () => {
    expect(parsePage({ page: '3' })).toBe(2)
    expect(parsePage({ page: '1' })).toBe(0)
    expect(parsePage({ page: '0' })).toBe(0)
    expect(parsePage({ page: 'abc' })).toBe(0)
    expect(parsePage({})).toBe(0)
  })

  it('fingerprints only the page query keys, in a fixed order', () => {
    expect(queryFingerprint({ page: '2', filter: 'FUNDS', unrelated: 'x' })).toBe(
      'filter=FUNDS&walletId=&type=&search=&sort=&page=2',
    )
  })
})

describe('useInvestmentFilters', () => {
  it('hydrates its state from the URL and loads that page when mounted', async () => {
    const { filters, onReload, onFiltersChange } = await mountFilters(
      '?filter=STOCKS&walletId=wallet-1&type=FII&search=petr&sort=gain,asc&page=2',
    )

    expect(filters.activeFilter.value).toBe('STOCKS')
    expect(filters.walletIdFilter.value).toBe('wallet-1')
    expect(filters.typeLabelFilter.value).toBe('FII')
    expect(filters.searchQuery.value).toBe('petr')
    expect(filters.sortKey.value).toBe('gain')
    expect(filters.sortDirection.value).toBe('asc')
    expect(onReload).toHaveBeenCalledExactlyOnceWith({
      kind: 'STOCKS',
      page: 1,
      walletId: 'wallet-1',
      typeLabel: 'FII',
      search: 'petr',
      sort: 'gain,asc',
    })
    expect(onFiltersChange).not.toHaveBeenCalled()
  })

  it('writes a tab change to the URL without reloading again for its own echo', async () => {
    const { filters, router, onReload, onFiltersChange } = await mountFilters(
      '?walletId=wallet-1&search=petr&sort=price,desc&page=3',
    )
    onReload.mockClear()

    filters.selectTab('CRYPTO')
    await flushPromises()

    expect(router.currentRoute.value.query).toEqual({ filter: 'CRYPTO', sort: 'price,desc' })
    expect(onFiltersChange).toHaveBeenCalledTimes(1)
    expect(onReload).toHaveBeenCalledExactlyOnceWith({
      kind: 'CRYPTO',
      page: 0,
      walletId: undefined,
      typeLabel: undefined,
      search: undefined,
      sort: 'price,desc',
    })
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
    expect(filters.typeLabelFilter.value).toBe('Renda Fixa')
    expect(filters.sortKey.value).toBeNull()
    expect(onFiltersChange).toHaveBeenCalledTimes(1)
    expect(onReload).toHaveBeenCalledExactlyOnceWith(
      expect.objectContaining({ kind: 'FUNDS', page: 1, typeLabel: 'Renda Fixa' }),
    )
    expect(router.currentRoute.value.query).toEqual({
      filter: 'FUNDS',
      type: 'Renda Fixa',
      page: '2',
    })
  })

  it('sets and clears the wallet and type filters, going back to the first page', async () => {
    const { filters, router, onReload } = await mountFilters('?filter=STOCKS&page=4')
    onReload.mockClear()

    filters.changeWallet('wallet-1')
    filters.changeType('FII')
    await flushPromises()
    expect(router.currentRoute.value.query).toEqual({
      filter: 'STOCKS',
      walletId: 'wallet-1',
      type: 'FII',
    })

    filters.changeWallet('')
    filters.changeType('')
    await flushPromises()
    expect(router.currentRoute.value.query).toEqual({ filter: 'STOCKS' })
    expect(onReload.mock.calls.map(([request]) => request.page)).toEqual([0, 0, 0, 0])
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

  it('builds the report query from the filters, without sort or page', async () => {
    const { filters } = await mountFilters(
      '?filter=STOCKS&walletId=wallet-1&type=FII&search=%20petr%20&sort=gain,asc&page=2',
    )

    expect(filters.reportQuery()).toEqual({
      filter: 'STOCKS',
      walletId: 'wallet-1',
      type: 'FII',
      search: 'petr',
    })
  })
})
