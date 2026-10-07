import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createMemoryHistory, createRouter } from 'vue-router'
import OverviewView from './OverviewView.vue'
import AreaChart from '@/components/charts/AreaChart.vue'
import DonutChart from '@/components/charts/DonutChart.vue'
import { ModalKey } from '@/composables/useModals'
import { useOverviewStore } from '@/stores/overview'
import { useWalletsStore } from '@/stores/wallets'
import type { PortfolioSummary, SeriesPoint, WalletKind, WalletResponse } from '@/types'

vi.mock('@/api/overview', () => ({ overviewApi: {} }))
vi.mock('@/api/wallets', () => ({ walletsApi: {} }))

const AreaChartStub = {
  props: ['data', 'xLabels', 'fmtY'],
  template: '<div class="area-chart-stub" />',
}
const DonutChartStub = {
  props: ['segments'],
  template: '<div class="donut-chart-stub"><slot /></div>',
}

const summary: PortfolioSummary = {
  displayCurrency: 'BRL',
  totalCostBasis: 10000,
  totalCurrentValue: 12000,
  totalGain: 2000,
  totalGainPct: 20,
  kindSummaries: [
    {
      kind: 'STOCKS',
      holdingCount: 3,
      totalCostBasis: 6000,
      totalCurrentValue: 7500,
      totalGain: 1500,
      totalGainPct: 25,
    },
    {
      kind: 'CRYPTO',
      holdingCount: 1,
      totalCostBasis: 4000,
      totalCurrentValue: 4500,
      totalGain: 500,
      totalGainPct: 12.5,
    },
  ],
}

function walletOf(id: string, kind: WalletKind): WalletResponse {
  return {
    id,
    name: `Carteira ${id}`,
    kind,
    currency: 'BRL',
    holdingCount: 1,
    totalInvested: 1000,
    currentValue: 1100,
    gain: 100,
    gainPct: 10,
    createdAt: '2026-01-01T00:00:00Z',
  }
}

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

async function mountView(options: {
  summary: PortfolioSummary | null
  series?: SeriesPoint[]
  wallets?: WalletResponse[]
}) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/overview', name: 'overview', component: OverviewView },
      { path: '/wallets', name: 'wallets', component: { template: '<div />' } },
      { path: '/investments', name: 'investments', component: { template: '<div />' } },
    ],
  })
  router.push('/overview')
  await router.isReady()

  const wrapper = mount(OverviewView, {
    global: {
      plugins: [createTestingPinia(), router],
      stubs: { AreaChart: AreaChartStub, DonutChart: DonutChartStub },
      provide: {
        [ModalKey as symbol]: {
          openAddInvestment: vi.fn(),
          openCreateWallet: vi.fn(),
          openPasswordChange: vi.fn(),
          openTrustedDevices: vi.fn(),
        },
      },
    },
  })
  const overviewStore = useOverviewStore()
  overviewStore.summary = options.summary
  overviewStore.series = options.series ?? []
  useWalletsStore().wallets = options.wallets ?? []
  await flushPromises()
  return { wrapper, router, overviewStore }
}

function kpiCards(wrapper: ReturnType<typeof mount>) {
  return wrapper.findAll('[data-testid="kpi"]')
}

function typeCard(wrapper: ReturnType<typeof mount>, label: string) {
  return wrapper.findAll('.type-card').find((card) => card.find('.type-name').text() === label)!
}

describe('OverviewView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('refreshes the overview and loads the wallets when mounted', async () => {
    const { overviewStore } = await mountView({ summary: null })

    expect(overviewStore.refresh).toHaveBeenCalledTimes(1)
    expect(useWalletsStore().load).toHaveBeenCalledTimes(1)
  })

  it('renders only the heading while there is no summary', async () => {
    const { wrapper } = await mountView({ summary: null })

    expect(wrapper.find('.page-title').text()).toBe('Visão geral')
    expect(wrapper.find('[data-testid="kpi"]').exists()).toBe(false)
    expect(wrapper.find('.type-card').exists()).toBe(false)
    expect(wrapper.find('.area-chart-stub').exists()).toBe(false)
  })

  it('renders the three summary figures from the store', async () => {
    const { wrapper } = await mountView({
      summary,
      wallets: [walletOf('1', 'STOCKS'), walletOf('2', 'CRYPTO')],
    })

    const cards = kpiCards(wrapper)

    expect(cards).toHaveLength(3)
    expect(cards[0].text()).toContain('Total investido')
    expect(cards[0].find('.kpi-value').text()).toBe('R$ 10,0k')
    expect(cards[0].text()).toContain('2 carteiras · 4 investimentos')
    expect(cards[1].text()).toContain('Valor atual estimado')
    expect(cards[1].find('.kpi-value').text()).toBe('R$ 12,0k')
    expect(cards[2].text()).toContain('Resultado')
    expect(cards[2].find('.kpi-value').text()).toBe('+R$ 2,0k')
    expect(cards[2].text()).toContain('+20,00%')
  })

  it('colours a positive result as a gain and a negative one as a loss', async () => {
    const gainView = await mountView({ summary })
    expect(kpiCards(gainView.wrapper)[2].find('.kpi-value').classes()).toContain('gl-up')

    const lossView = await mountView({
      summary: { ...summary, totalGain: -500, totalGainPct: -5 },
    })
    const lossCard = kpiCards(lossView.wrapper)[2]

    expect(lossCard.find('.kpi-value').classes()).toContain('gl-down')
    expect(lossCard.find('.kpi-value').text()).toBe('−R$ 500,00')
    expect(lossCard.text()).toContain('−5,00%')
  })

  it('treats a missing gain percentage as zero', async () => {
    const { wrapper } = await mountView({ summary: { ...summary, totalGainPct: null } })

    expect(kpiCards(wrapper)[2].text()).toContain('+0,00%')
  })

  it('lists one type card per kind, including kinds absent from the summary', async () => {
    const { wrapper } = await mountView({
      summary,
      wallets: [walletOf('1', 'STOCKS'), walletOf('2', 'STOCKS'), walletOf('3', 'CRYPTO')],
    })

    expect(wrapper.findAll('.type-card').map((card) => card.find('.type-name').text())).toEqual([
      'Ações',
      'Cripto',
      'Fundos',
    ])

    const stocks = typeCard(wrapper, 'Ações')
    expect(stocks.find('.type-meta').text()).toContain('2 carteiras')
    expect(stocks.find('.type-meta').text()).toContain('3 ativos')
    expect(stocks.find('.type-value').text()).toBe('R$ 6,0k')
    expect(stocks.find('.result-value').text()).toBe('R$ 7,5k')
    expect(stocks.find('.gl').text()).toContain('+R$ 1,5k')
    expect(stocks.find('.gl').text()).toContain('+25,00%')

    const crypto = typeCard(wrapper, 'Cripto')
    expect(crypto.find('.type-meta').text()).toContain('1 carteira')
    expect(crypto.find('.type-meta').text()).not.toContain('1 carteiras')
    expect(crypto.find('.type-meta').text()).toContain('1 ativo')

    const funds = typeCard(wrapper, 'Fundos')
    expect(funds.find('.type-meta').text()).toContain('0 carteiras')
    expect(funds.find('.type-meta').text()).toContain('0 ativos')
    expect(funds.find('.type-value').text()).toBe('R$ 0,00')
    expect(funds.find('.gl-empty').text()).toBe('—')
  })

  it('shows each kind share of the invested total in the allocation legend', async () => {
    const { wrapper } = await mountView({ summary })

    const legendRows = wrapper.findAll('.alloc-legend li').map((item) => item.text())

    expect(legendRows).toEqual([
      expect.stringContaining('Ações'),
      expect.stringContaining('Cripto'),
      expect.stringContaining('Fundos'),
    ])
    expect(legendRows[0]).toContain('60,00%')
    expect(legendRows[1]).toContain('40,00%')
    expect(legendRows[2]).toContain('0,00%')
  })

  it('feeds the donut only the kinds that have something invested', async () => {
    const { wrapper } = await mountView({ summary })

    const segments = wrapper.findComponent(DonutChart).props('segments')

    expect(
      segments.map((segment: { label: string; value: number }) => [segment.label, segment.value]),
    ).toEqual([
      ['Ações', 6000],
      ['Cripto', 4000],
    ])
    expect(wrapper.find('.donut-center-value').text()).toBe('R$ 10,0k')
  })

  it('feeds the donut a single placeholder segment when nothing is invested', async () => {
    const { wrapper } = await mountView({
      summary: { ...summary, totalCostBasis: 0, kindSummaries: [] },
    })

    const segments = wrapper.findComponent(DonutChart).props('segments')

    expect(segments).toHaveLength(1)
    expect(segments[0].value).toBe(1)
    expect(segments[0].label).toBe('—')
    expect(wrapper.findAll('.legend-pct').map((cell) => cell.text())).toEqual(['0%', '0%', '0%'])
  })

  it('feeds the area chart the invested series with month labels and shows the latest value', async () => {
    const { wrapper } = await mountView({
      summary,
      series: [
        { month: '2026-01', totalInvested: 1000 },
        { month: '2026-02', totalInvested: 4000 },
        { month: '2026-03', totalInvested: 10000 },
      ],
    })

    const chart = wrapper.findComponent(AreaChart)

    expect(chart.props('data')).toEqual([1000, 4000, 10000])
    expect(chart.props('xLabels')).toEqual(['jan/26', 'fev/26', 'mar/26'])
    expect(wrapper.find('.chart-big').text()).toBe('R$ 10,0k')
    expect(wrapper.find('.sub-caption').text()).toContain('BRL')
  })

  it('pads a single-point series with a leading zero so the chart can draw a line', async () => {
    const { wrapper } = await mountView({
      summary,
      series: [{ month: '2026-03', totalInvested: 2500 }],
    })

    const chart = wrapper.findComponent(AreaChart)

    expect(chart.props('data')).toEqual([0, 2500])
    expect(chart.props('xLabels')).toEqual(['', 'mar/26'])
  })

  it('feeds the area chart a flat zero series when there is no history', async () => {
    const { wrapper } = await mountView({ summary, series: [] })

    const chart = wrapper.findComponent(AreaChart)

    expect(chart.props('data')).toEqual([0, 0])
    expect(chart.props('xLabels')).toEqual(['', ''])
    expect(wrapper.find('.chart-big').text()).toBe('R$ 0,00')
  })

  it('formats the area chart axis ticks in thousands above one thousand', async () => {
    const { wrapper } = await mountView({ summary })

    const formatAxis = wrapper.findComponent(AreaChart).props('fmtY') as (value: number) => string

    expect(formatAxis(500)).toBe('R$ 500')
    expect(formatAxis(12000)).toBe('R$ 12k')
  })

  it('uses the summary display currency for every figure', async () => {
    const { wrapper } = await mountView({ summary: { ...summary, displayCurrency: 'USD' } })

    expect(kpiCards(wrapper)[0].find('.kpi-value').text()).toBe('US$ 10,0k')
    expect(wrapper.find('.sub-caption').text()).toContain('USD')
  })

  it('opens the investments list filtered by kind when a type card is clicked', async () => {
    const { wrapper, router } = await mountView({ summary })

    await typeCard(wrapper, 'Cripto').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('investments')
    expect(router.currentRoute.value.query.filter).toBe('CRYPTO')
  })

  it('navigates to the wallets page from the Carteiras button', async () => {
    const { wrapper, router } = await mountView({ summary: null })

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Carteiras')!
      .trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('wallets')
  })

  it('shows the loading overlay only while the overview store is loading', async () => {
    const { wrapper, overviewStore } = await mountView({ summary: null })

    expect(wrapper.find('.loading-overlay').exists()).toBe(false)

    overviewStore.loading = true
    await flushPromises()
    expect(wrapper.findAll('.loading-overlay')).toHaveLength(1)

    overviewStore.loading = false
    await flushPromises()
    expect(wrapper.find('.loading-overlay').exists()).toBe(false)
  })
})
