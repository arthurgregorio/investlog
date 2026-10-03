import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createMemoryHistory, createRouter } from 'vue-router'
import ResultsDashboardView from './ResultsDashboardView.vue'
import { useResultsStore } from '@/stores/results'
import { useReinvestmentsStore } from '@/stores/reinvestments'
import type { ReinvestmentRow, ResultSummary } from '@/types'

vi.mock('@/api/results', () => ({ resultsApi: { getSummary: vi.fn(), findAll: vi.fn() } }))
vi.mock('@/api/reinvestments', () => ({
  reinvestmentsApi: { findAll: vi.fn(), reinvest: vi.fn() },
}))

const summary: ResultSummary = {
  displayCurrency: 'BRL',
  totalWithdrawn: 800,
  totalNetReceived: 790,
  totalProfit: 190,
  totalFees: 7,
  totalTaxes: 3,
  exitCount: 2,
}

const reinvestment: ReinvestmentRow = {
  id: 'reinvestment-1',
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
  profit: 500,
}

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

async function mountView(options: {
  summary: ResultSummary
  reinvestments: ReinvestmentRow[]
  totalElements?: number
  totalPages?: number
}) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/overview/resultados', name: 'overview-results', component: ResultsDashboardView },
      { path: '/wallets', name: 'wallets', component: {} },
    ],
  })
  router.push('/overview/resultados')
  await router.isReady()

  const pinia = createTestingPinia()
  const resultsStore = useResultsStore()
  const reinvestmentsStore = useReinvestmentsStore()
  resultsStore.summary = options.summary
  reinvestmentsStore.rows = options.reinvestments
  reinvestmentsStore.totalElements = options.totalElements ?? options.reinvestments.length
  reinvestmentsStore.totalPages = options.totalPages ?? 1
  reinvestmentsStore.pageSize = 10
  reinvestmentsStore.loaded = true

  const wrapper = mount(ResultsDashboardView, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return { wrapper, router, resultsStore, reinvestmentsStore }
}

describe('ResultsDashboardView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loads the summary and the first ten reinvestments', async () => {
    const { resultsStore, reinvestmentsStore } = await mountView({
      summary,
      reinvestments: [reinvestment],
    })

    expect(resultsStore.loadSummary).toHaveBeenCalled()
    expect(reinvestmentsStore.load).toHaveBeenCalledWith(0, 10)
  })

  it('shows the four realised totals from the summary', async () => {
    const { wrapper } = await mountView({ summary, reinvestments: [reinvestment] })

    expect(wrapper.get('[data-testid="kpi-withdrawn"]').text()).toBe('R$ 800,00')
    expect(wrapper.get('[data-testid="kpi-profit"]').text()).toBe('+R$ 190,00')
    expect(wrapper.get('[data-testid="kpi-fees"]').text()).toBe('R$ 7,00')
    expect(wrapper.get('[data-testid="kpi-taxes"]').text()).toBe('R$ 3,00')
  })

  it('shows both sides of each reinvestment and its result', async () => {
    const { wrapper } = await mountView({ summary, reinvestments: [reinvestment] })

    const row = wrapper.get('[data-testid="reinvestment-row"]')
    expect(row.text()).toContain('PETR4')
    expect(row.text()).toContain('Ações')
    expect(row.text()).toContain('Tesouro Selic')
    expect(row.text()).toContain('Fundos')
    expect(row.text()).toContain('+R$ 500,00')
  })

  it('offers pagination only when there is more than one page', async () => {
    const single = await mountView({ summary, reinvestments: [reinvestment] })
    expect(single.wrapper.find('.pagination').exists()).toBe(false)

    const several = await mountView({
      summary,
      reinvestments: [reinvestment],
      totalElements: 25,
      totalPages: 3,
    })
    expect(several.wrapper.find('.pagination').exists()).toBe(true)
  })

  it('loads the next page of ten when paginating forward', async () => {
    const { wrapper, reinvestmentsStore } = await mountView({
      summary,
      reinvestments: [reinvestment],
      totalElements: 25,
      totalPages: 3,
    })

    await wrapper.get('.pagination-next').trigger('click')
    await flushPromises()

    expect(reinvestmentsStore.load).toHaveBeenLastCalledWith(1, 10)
  })

  it('shows an empty state instead of zeroed totals when nothing was realised', async () => {
    const { wrapper } = await mountView({
      summary: {
        ...summary,
        totalWithdrawn: 0,
        totalProfit: 0,
        totalFees: 0,
        totalTaxes: 0,
        exitCount: 0,
      },
      reinvestments: [],
    })

    expect(wrapper.find('[data-testid="results-empty"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="kpi-withdrawn"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="reinvestments"]').exists()).toBe(false)
  })

  it('explains the missing reinvestments when only withdrawals were recorded', async () => {
    const { wrapper } = await mountView({ summary, reinvestments: [] })

    expect(wrapper.find('[data-testid="kpi-withdrawn"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="reinvestments"]').text()).toContain('Nenhum reinvestimento')
  })
})
