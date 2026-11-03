import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createMemoryHistory, createRouter } from 'vue-router'
import OverviewView from './OverviewView.vue'
import { ModalKey } from '@/composables/useModals'
import { useOverviewStore } from '@/stores/overview'
import { useWalletsStore } from '@/stores/wallets'
import type {
  MonthSummary,
  PortfolioSummary,
  SeriesPoint,
  WalletKind,
  WalletResponse,
} from '@/types'

vi.mock('@/api/overview', () => ({ overviewApi: {} }))
vi.mock('@/api/wallets', () => ({ walletsApi: {} }))

const PatrimonyChartStub = {
  props: ['ownMoney', 'result', 'labels', 'markerIndex', 'markerLabel', 'fmtY'],
  template: '<div class="patrimony-chart-stub" />',
}

const summary: PortfolioSummary = {
  displayCurrency: 'BRL',
  totalCostBasis: 10000,
  totalCurrentValue: 12500,
  totalCash: 500,
  totalGain: 2000,
  totalGainPct: 20,
  positionsValue: 12000,
  ownMoney: 9000,
  realizedProfit: 1500,
  lifetimeResult: 3500,
  lifetimeResultPct: 38.89,
  unpricedHoldingCount: 0,
  cashWalletCount: 1,
  kindSummaries: [
    {
      kind: 'STOCKS',
      holdingCount: 3,
      walletCount: 2,
      totalCostBasis: 6000,
      totalCurrentValue: 7500,
      totalGain: 1500,
      totalGainPct: 25,
    },
  ],
}

const series: SeriesPoint[] = [
  {
    month: '2026-10',
    ownMoney: 8000,
    reinvestedProfit: 1000,
    totalInvested: 9000,
    result: 1000,
    marketValued: false,
  },
  {
    month: '2026-11',
    ownMoney: 9000,
    reinvestedProfit: 1500,
    totalInvested: 10500,
    result: 3500,
    marketValued: true,
  },
]

const month: MonthSummary = {
  month: '2026-11',
  contributions: 1000,
  realizedProfit: 500,
  currentResultChange: 300,
  bestMover: null,
  worstMover: null,
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
    cash: 0,
    cashReconciled: true,
    createdAt: '2026-01-01T00:00:00Z',
  }
}

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

async function mountView(options: {
  summary: PortfolioSummary | null
  series?: SeriesPoint[]
  month?: MonthSummary | null
  wallets?: WalletResponse[]
}) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/overview', name: 'overview', component: OverviewView },
      {
        path: '/overview/resultados',
        name: 'overview-results',
        component: { template: '<div />' },
      },
      { path: '/wallets', name: 'wallets', component: { template: '<div />' } },
      { path: '/investments', name: 'investments', component: { template: '<div />' } },
    ],
  })
  router.push('/overview')
  await router.isReady()

  const modals = {
    openAddInvestment: vi.fn(),
    openCreateWallet: vi.fn(),
    openPasswordChange: vi.fn(),
    openTrustedDevices: vi.fn(),
  }
  const wrapper = mount(OverviewView, {
    global: {
      plugins: [createTestingPinia(), router],
      stubs: { PatrimonyChart: PatrimonyChartStub },
      provide: { [ModalKey as symbol]: modals },
    },
  })
  const overviewStore = useOverviewStore()
  overviewStore.summary = options.summary
  overviewStore.series = options.series ?? []
  overviewStore.month = options.month === undefined ? month : options.month
  useWalletsStore().wallets = options.wallets ?? []
  await flushPromises()
  return { wrapper, router, overviewStore, modals }
}

async function clickTestId(wrapper: ReturnType<typeof mount>, testId: string) {
  await wrapper.get(`[data-testid="${testId}"]`).trigger('click')
  await flushPromises()
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

  it('renders only the heading, with no header button, while there is no summary', async () => {
    const { wrapper } = await mountView({ summary: null })

    expect(wrapper.find('.page-title').text()).toBe('Visão geral')
    expect(wrapper.find('.head-actions').exists()).toBe(false)
    expect(wrapper.find('[data-testid="overview-health-card"]').exists()).toBe(false)
  })

  it('shows an empty state with a create-wallet action when there is nothing to summarise', async () => {
    const { wrapper, modals } = await mountView({
      summary: { ...summary, kindSummaries: [], totalCash: 0 },
      series: [],
    })

    expect(wrapper.find('.empty-title').text()).toBe('Nada para resumir ainda')
    expect(wrapper.find('[data-testid="overview-health-card"]').exists()).toBe(false)

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Criar carteira')!
      .trigger('click')

    expect(modals.openCreateWallet).toHaveBeenCalledTimes(1)
  })

  it('renders the health card, the chart, the month and the four money cards', async () => {
    const { wrapper } = await mountView({
      summary,
      series,
      wallets: [walletOf('1', 'STOCKS'), walletOf('2', 'STOCKS')],
    })

    expect(wrapper.get('[data-testid="lifetime-value"]').text()).toBe('+R$ 3,5k')
    expect(wrapper.get('[data-testid="current-value"]').text()).toBe('+R$ 2,0k')
    expect(wrapper.find('.patrimony-chart-stub').exists()).toBe(true)
    expect(wrapper.find('[data-testid="this-month-card"]').exists()).toBe(true)
    expect(wrapper.get('.section-label').text()).toBe('Onde está o dinheiro')
    expect(
      wrapper
        .findAll('[data-testid="type-summary-card"]')
        .map((card) => card.get('.type-name').text()),
    ).toEqual(['Ações', 'Cripto', 'Fundos'])
    expect(wrapper.get('[data-testid="cash-summary-card"] .type-value').text()).toBe('R$ 500,00')
  })

  it('hides the month card until the month summary arrives', async () => {
    const { wrapper } = await mountView({ summary, month: null })

    expect(wrapper.find('[data-testid="this-month-card"]').exists()).toBe(false)
  })

  it('leads from the health card to the results dashboard and the investments list', async () => {
    const { wrapper, router } = await mountView({ summary })

    await clickTestId(wrapper, 'view-results')
    expect(router.currentRoute.value.name).toBe('overview-results')

    await clickTestId(wrapper, 'view-investments')
    expect(router.currentRoute.value.name).toBe('investments')
  })

  it('opens the investments list from the price-status strip', async () => {
    const { wrapper, router } = await mountView({
      summary: { ...summary, unpricedHoldingCount: 2 },
    })

    await clickTestId(wrapper, 'price-status-update')

    expect(router.currentRoute.value.name).toBe('investments')
  })

  it('opens the investments list filtered by kind from a kind card', async () => {
    const { wrapper, router } = await mountView({
      summary,
      wallets: [walletOf('1', 'STOCKS')],
    })

    await clickTestId(wrapper, 'type-view')

    expect(router.currentRoute.value.name).toBe('investments')
    expect(router.currentRoute.value.query.filter).toBe('STOCKS')
  })

  it('offers to create a wallet of a kind the user has none of', async () => {
    const { wrapper, modals } = await mountView({
      summary,
      wallets: [walletOf('1', 'STOCKS')],
    })

    await wrapper.findAll('[data-testid="type-create"]')[0]!.trigger('click')

    expect(modals.openCreateWallet).toHaveBeenCalledWith('CRYPTO')
  })

  it('opens the wallets page from the cash card', async () => {
    const { wrapper, router } = await mountView({ summary })

    await clickTestId(wrapper, 'cash-view')

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
