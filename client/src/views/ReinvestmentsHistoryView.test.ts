import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createMemoryHistory, createRouter } from 'vue-router'
import ReinvestmentsHistoryView from './ReinvestmentsHistoryView.vue'
import { useReinvestmentsStore } from '@/stores/reinvestments'
import type { ReinvestmentRow } from '@/types'

vi.mock('@/api/reinvestments', () => ({
  reinvestmentsApi: { findAll: vi.fn(), reinvest: vi.fn() },
}))

function reinvestmentOf(id: string, profit: number): ReinvestmentRow {
  return {
    id,
    reinvestmentDate: '2026-09-30',
    currency: 'BRL',
    source: {
      holdingId: 'holding-1',
      kind: 'STOCKS',
      name: 'Petrobras',
      ticker: 'PETR4',
      walletId: 'wallet-1',
      walletName: 'Ações',
    },
    destination: {
      holdingId: 'holding-2',
      kind: 'FUNDS',
      name: 'Tesouro Selic',
      ticker: null,
      walletId: 'wallet-2',
      walletName: 'Fundos',
    },
    quantity: 40,
    grossAmount: 2000,
    fees: 10,
    taxes: 90,
    amount: 1900,
    profit,
  }
}

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

async function mountView(rows: ReinvestmentRow[], totalPages = 1) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/overview/reinvestimentos',
        name: 'overview-reinvestments',
        component: ReinvestmentsHistoryView,
      },
      { path: '/overview/resultados', name: 'overview-results', component: {} },
    ],
  })
  router.push('/overview/reinvestimentos')
  await router.isReady()

  const pinia = createTestingPinia()
  const store = useReinvestmentsStore()
  store.rows = rows
  store.totalElements = rows.length * totalPages
  store.totalPages = totalPages
  store.pageSize = 20
  store.loaded = true

  const wrapper = mount(ReinvestmentsHistoryView, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return { wrapper, store }
}

describe('ReinvestmentsHistoryView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loads the first page at the full history page size', async () => {
    const { store } = await mountView([reinvestmentOf('reinvestment-1', 500)])

    expect(store.load).toHaveBeenCalledWith(0, 20)
  })

  it('lists every reinvestment with both sides and its result', async () => {
    const { wrapper } = await mountView([
      reinvestmentOf('reinvestment-1', 500),
      reinvestmentOf('reinvestment-2', -120),
    ])

    const rows = wrapper.findAll('[data-testid="reinvestment-row"]')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('+R$ 500,00')
    expect(rows[1].text()).toContain('−R$ 120,00')
    expect(rows[1].text()).toContain('Tesouro Selic')
  })

  it('offers pagination only when there is more than one page', async () => {
    const single = await mountView([reinvestmentOf('reinvestment-1', 500)], 1)
    expect(single.wrapper.find('.pagination').exists()).toBe(false)

    const several = await mountView([reinvestmentOf('reinvestment-1', 500)], 3)
    expect(several.wrapper.find('.pagination').exists()).toBe(true)
  })

  it('shows an empty state when no reinvestment was recorded', async () => {
    const { wrapper } = await mountView([])

    expect(wrapper.text()).toContain('Nenhum reinvestimento')
    expect(wrapper.find('[data-testid="reinvestment-row"]').exists()).toBe(false)
  })
})
