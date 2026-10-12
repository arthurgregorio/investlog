import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createMemoryHistory, createRouter } from 'vue-router'
import ResultsDashboardView from './ResultsDashboardView.vue'
import { useResultsStore } from '@/stores/results'
import { useReinvestmentsStore } from '@/stores/reinvestments'
import type { ReinvestmentRow, ResultRow, ResultSummary } from '@/types'

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

const withdrawal: ResultRow = {
  id: 'result-1',
  kind: 'STOCKS',
  resultType: 'WITHDRAWAL',
  holdingName: 'Vale',
  ticker: 'VALE3',
  walletId: 'wallet-1',
  walletName: 'Ações',
  walletCurrency: 'BRL',
  resultDate: '2026-09-19',
  quantity: 10,
  grossAmount: 700,
  fees: 2,
  taxes: 3,
  costBasis: 600,
  netAmount: 695,
  profit: 95,
}

async function mountView(options: {
  summary: ResultSummary
  reinvestments: ReinvestmentRow[]
  totalElements?: number
  totalPages?: number
  withdrawals?: ResultRow[]
  withdrawalsTotalElements?: number
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
  const withdrawals = options.withdrawals ?? [withdrawal]
  resultsStore.rows = withdrawals
  resultsStore.totalElements = options.withdrawalsTotalElements ?? withdrawals.length
  resultsStore.pageSize = 10
  resultsStore.loaded = true

  const wrapper = mount(ResultsDashboardView, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return { wrapper, router, resultsStore, reinvestmentsStore }
}

describe('ResultsDashboardView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loads the summary, the first ten reinvestments and the first ten withdrawals', async () => {
    const { resultsStore, reinvestmentsStore } = await mountView({
      summary,
      reinvestments: [reinvestment],
    })

    expect(resultsStore.loadSummary).toHaveBeenCalled()
    expect(reinvestmentsStore.load).toHaveBeenCalledWith(0, 10)
    expect(resultsStore.load).toHaveBeenCalledWith(0, 10)
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
    expect(several.wrapper.get('[data-testid="reinvestments"]').find('.pagination').exists()).toBe(
      true,
    )
    expect(several.wrapper.get('[data-testid="withdrawals"]').find('.pagination').exists()).toBe(
      false,
    )
  })

  it('loads the next page of ten when paginating forward', async () => {
    const { wrapper, reinvestmentsStore, resultsStore } = await mountView({
      summary,
      reinvestments: [reinvestment],
      totalElements: 25,
      totalPages: 3,
    })
    vi.mocked(resultsStore.load).mockClear()

    await wrapper.get('[data-testid="reinvestments"] .pagination-next').trigger('click')
    await flushPromises()

    expect(reinvestmentsStore.load).toHaveBeenLastCalledWith(1, 10)
    expect(resultsStore.load).not.toHaveBeenCalled()
  })

  it('renders the Resgates card below the Reinvestimentos card', async () => {
    const { wrapper } = await mountView({ summary, reinvestments: [reinvestment] })

    expect(wrapper.findAll('.table-card').map((card) => card.attributes('data-testid'))).toEqual([
      'reinvestments',
      'withdrawals',
    ])
    const withdrawalsCard = wrapper.get('[data-testid="withdrawals"]')
    expect(withdrawalsCard.text()).toContain('Resgates')
    expect(withdrawalsCard.text()).toContain('Do mais recente para o mais antigo')
  })

  it('lists each withdrawal with its result', async () => {
    const { wrapper } = await mountView({ summary, reinvestments: [reinvestment] })

    const row = wrapper.get('[data-testid="withdrawal-row"]')
    expect(row.text()).toContain('VALE3')
    expect(row.text()).toContain('Vale · Ações')
    expect(row.text()).toContain('R$ 70,00')
    expect(row.text()).toContain('+R$ 95,00')
  })

  it('pages through the withdrawals ten at a time', async () => {
    const { wrapper, reinvestmentsStore, resultsStore } = await mountView({
      summary,
      reinvestments: [reinvestment],
      withdrawalsTotalElements: 25,
    })
    vi.mocked(reinvestmentsStore.load).mockClear()

    await wrapper.get('[data-testid="withdrawals"] .pagination-next').trigger('click')
    await flushPromises()

    expect(resultsStore.load).toHaveBeenLastCalledWith(1, 10)
    expect(reinvestmentsStore.load).not.toHaveBeenCalled()
  })

  it('explains the missing withdrawals when only reinvestments were recorded', async () => {
    const { wrapper } = await mountView({
      summary,
      reinvestments: [reinvestment],
      withdrawals: [],
    })

    expect(wrapper.get('[data-testid="withdrawals"]').text()).toContain('Nenhum resgate')
    expect(wrapper.find('[data-testid="withdrawal-row"]').exists()).toBe(false)
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
    expect(wrapper.find('[data-testid="withdrawals"]').exists()).toBe(false)
  })

  it('explains the missing reinvestments when only withdrawals were recorded', async () => {
    const { wrapper } = await mountView({ summary, reinvestments: [] })

    expect(wrapper.find('[data-testid="kpi-withdrawn"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="reinvestments"]').text()).toContain('Nenhum reinvestimento')
  })

  it('shows the loading overlay only while the results summary is loading', async () => {
    const { wrapper, resultsStore } = await mountView({ summary, reinvestments: [reinvestment] })

    expect(wrapper.find('.loading-overlay').exists()).toBe(false)

    resultsStore.summaryLoading = true
    await flushPromises()
    expect(wrapper.findAll('.loading-overlay')).toHaveLength(1)

    resultsStore.summaryLoading = false
    await flushPromises()
    expect(wrapper.find('.loading-overlay').exists()).toBe(false)
  })

  it('covers the withdrawals load with the page loading overlay', async () => {
    const { wrapper, resultsStore } = await mountView({ summary, reinvestments: [reinvestment] })

    resultsStore.loading = true
    await flushPromises()
    expect(wrapper.findAll('.loading-overlay')).toHaveLength(1)

    resultsStore.loading = false
    await flushPromises()
    expect(wrapper.find('.loading-overlay').exists()).toBe(false)
  })
})
