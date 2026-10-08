import { describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createMemoryHistory, createRouter } from 'vue-router'
import { useReportFilters } from './useReportFilters'
import { useWalletsStore } from '@/stores/wallets'
import type { WalletResponse } from '@/types'

async function filtersFor(query: string, knownWallet?: WalletResponse) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/investments/report', component: { render: () => null } }],
  })
  router.push(`/investments/report${query}`)
  await router.isReady()

  const pinia = createTestingPinia()
  vi.mocked(useWalletsStore().walletById).mockReturnValue(knownWallet)

  let filters!: ReturnType<typeof useReportFilters>
  mount(
    defineComponent({
      setup() {
        filters = useReportFilters()
        return () => h('div')
      },
    }),
    { global: { plugins: [pinia, router] } },
  )
  return filters
}

describe('useReportFilters', () => {
  it('has no filter and no label without query parameters', async () => {
    const filters = await filtersFor('')

    expect(filters.kindFilter.value).toBeUndefined()
    expect(filters.walletIdFilter.value).toBeUndefined()
    expect(filters.typeLabelFilter.value).toBeUndefined()
    expect(filters.searchFilter.value).toBeUndefined()
    expect(filters.activeFilterLabels.value).toEqual([])
  })

  it('reads every filter from the query string and labels them in order', async () => {
    const filters = await filtersFor(
      '?filter=CRYPTO&type=Ordin%C3%A1ria&walletId=wallet-9&search=btc',
    )

    expect(filters.kindFilter.value).toBe('CRYPTO')
    expect(filters.typeLabelFilter.value).toBe('Ordinária')
    expect(filters.walletIdFilter.value).toBe('wallet-9')
    expect(filters.searchFilter.value).toBe('btc')
    expect(filters.activeFilterLabels.value).toEqual([
      'Cripto',
      'Ordinária',
      'Carteira selecionada',
      '"btc"',
    ])
  })

  it('ignores a kind that is not a wallet kind', async () => {
    const filters = await filtersFor('?filter=BONDS')

    expect(filters.kindFilter.value).toBeUndefined()
    expect(filters.activeFilterLabels.value).toEqual([])
  })

  it('ignores a parameter repeated into an array', async () => {
    const filters = await filtersFor('?search=a&search=b')

    expect(filters.searchFilter.value).toBeUndefined()
  })

  it('names the filtered wallet when the wallets store knows it', async () => {
    const filters = await filtersFor('?walletId=wallet-1', {
      id: 'wallet-1',
      name: 'Ações BR',
    } as WalletResponse)

    expect(filters.activeFilterLabels.value).toEqual(['Ações BR'])
  })
})
