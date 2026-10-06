import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createRouter, createMemoryHistory } from 'vue-router'
import WalletDetailView from './WalletDetailView.vue'
import { useWalletDetailStore } from '@/stores/walletDetail'
import { useHoldingsListStore } from '@/stores/holdingsList'
import { useAuthStore } from '@/stores/auth'
import { useWalletMovesStore } from '@/stores/walletMoves'
import { useWalletsStore } from '@/stores/wallets'
import { useReinvestmentsStore } from '@/stores/reinvestments'
import { holdingsApi } from '@/api/holdings'
import type { HoldingRow, WalletDetail, WalletMoveRow, WalletResponse } from '@/types'

vi.mock('@/api/walletMoves', () => ({ walletMovesApi: { findAll: vi.fn(), move: vi.fn() } }))
vi.mock('@/api/reinvestments', () => ({
  reinvestmentsApi: { findAll: vi.fn(), reinvest: vi.fn() },
}))
vi.mock('@/api/walletDetail', () => ({ walletDetailApi: { get: vi.fn() } }))
vi.mock('@/api/wallets', () => ({
  walletsApi: { findAll: vi.fn(), create: vi.fn(), update: vi.fn(), remove: vi.fn() },
}))
vi.mock('@/api/holdings', () => ({
  holdingsApi: {
    findAll: vi.fn(),
    findAllForReport: vi.fn(),
    getStockHolding: vi.fn(),
    getCryptoHolding: vi.fn(),
    getFundHolding: vi.fn(),
  },
}))

function detailOf(overrides: Partial<WalletDetail> = {}): WalletDetail {
  return {
    id: 'wallet-1',
    name: 'Detail Wallet',
    kind: 'STOCKS',
    currency: 'BRL',
    currentValue: 4750,
    totalInvested: 4500,
    gain: 250,
    gainPct: 5.5556,
    series: [],
    dayChange: null,
    weekChange: null,
    monthChange: null,
    bestPerformer: null,
    worstPerformer: null,
    largestHoldingName: null,
    largestHoldingShare: null,
    activity: {
      lastTransactionDate: null,
      lastTransactionName: null,
      lastTransactionAmount: null,
      transactionCount: 0,
      walletAgeInDays: null,
      investmentCount: 2,
    },
    ...overrides,
  }
}

const mockRow: HoldingRow = {
  id: 'holding-1',
  kind: 'STOCKS',
  name: 'Petrobras',
  ticker: 'PETR4',
  typeLabel: 'Ação ON',
  walletId: 'wallet-1',
  walletName: 'Detail Wallet',
  walletCurrency: 'BRL',
  quantity: 100,
  costBasis: 3500,
  currentPrice: 38.5,
  currentValue: 3850,
  gain: 350,
  gainPct: 10,
  frozen: false,
}

function control(wrapper: ReturnType<typeof mount>, testId: string) {
  const element = wrapper.find(`[data-testid="${testId}"]`)
  return ['INPUT', 'SELECT'].includes(element.element.tagName)
    ? element
    : element.find('input, select')
}

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

async function mountView(
  detail: WalletDetail | null,
  rows: HoldingRow[] = [mockRow],
  loaded = true,
  isAdmin = true,
  moves: WalletMoveRow[] = [],
) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/wallets', name: 'wallets', component: { template: '<div />' } },
      { path: '/wallets/:id', name: 'wallet-detail', component: WalletDetailView },
      { path: '/investments', name: 'investments', component: { template: '<div />' } },
    ],
  })
  const pinia = createTestingPinia()

  router.push('/wallets/wallet-1')
  await router.isReady()

  const wrapper = mount(WalletDetailView, { global: { plugins: [pinia, router] } })

  const walletDetailStore = useWalletDetailStore()
  const holdingsListStore = useHoldingsListStore()
  const authStore = useAuthStore()
  authStore.session = {
    name: 'Admin',
    email: 'admin@admin.com',
    role: isAdmin ? 'ADMIN' : 'USER',
    status: 'APPROVED',
    authProvider: 'LOCAL',
    demoModeEnabled: false,
  }
  walletDetailStore.detail = detail
  holdingsListStore.rows = rows
  holdingsListStore.loaded = loaded
  const walletMovesStore = useWalletMovesStore()
  walletMovesStore.rows = moves
  walletMovesStore.loaded = true

  await flushPromises()
  return { wrapper, router, walletDetailStore, holdingsListStore, walletMovesStore }
}

describe('WalletDetailView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(holdingsApi.findAllForReport).mockResolvedValue([])
  })

  it('loads every holding of the wallet for the allocation donut, not just the current page', async () => {
    await mountView(detailOf())

    expect(holdingsApi.findAllForReport).toHaveBeenCalledTimes(1)
    expect(holdingsApi.findAllForReport).toHaveBeenCalledWith({ walletId: 'wallet-1' })
  })

  it('renders the allocation legend from the report rows and toggles without another request', async () => {
    vi.mocked(holdingsApi.findAllForReport).mockResolvedValue([
      { ...mockRow, id: 'a', ticker: 'AAAA3', costBasis: 700, currentValue: 250 },
      { ...mockRow, id: 'b', ticker: 'BBBB3', costBasis: 200, currentValue: 750 },
    ])
    const { wrapper } = await mountView(detailOf())

    const entries = () =>
      wrapper
        .find('[data-testid="allocation-card"]')
        .findAll('[data-testid="allocation-legend-entry"]')
        .map((entry) => entry.text())
    expect(entries()[0]).toContain('BBBB3')
    expect(entries()[0]).toContain('75,00%')

    await wrapper.find('[data-testid="allocation-metric-costBasis"]').trigger('click')
    expect(entries()[0]).toContain('AAAA3')
    expect(entries()[0]).toContain('77,78%')

    await wrapper.find('[data-testid="allocation-metric-currentValue"]').trigger('click')
    expect(entries()[0]).toContain('BBBB3')
    expect(holdingsApi.findAllForReport).toHaveBeenCalledTimes(1)
  })

  it('shows the allocation empty state for a wallet with no holdings', async () => {
    const { wrapper } = await mountView(detailOf())

    expect(wrapper.find('[data-testid="allocation-card"]').text()).toContain(
      'Nenhum investimento nesta carteira',
    )
    expect(wrapper.find('canvas').exists()).toBe(false)
  })

  it('loads the wallet move history on mount', async () => {
    const { walletMovesStore } = await mountView(detailOf())

    expect(walletMovesStore.load).toHaveBeenCalledWith('wallet-1', 0)
  })

  it('lists relocations into and out of the wallet', async () => {
    const { wrapper } = await mountView(detailOf(), [mockRow], true, true, [
      {
        id: 'move-out',
        movedAt: '2026-09-20',
        direction: 'OUT',
        kind: 'STOCKS',
        holdingName: 'Vale',
        ticker: 'VALE3',
        quantity: 10,
        originWalletId: 'wallet-1',
        originWalletName: 'Detail Wallet',
        destinationWalletId: 'wallet-2',
        destinationWalletName: 'Outra carteira',
      },
      {
        id: 'move-in',
        movedAt: '2026-09-21',
        direction: 'IN',
        kind: 'STOCKS',
        holdingName: 'Itaú',
        ticker: 'ITUB4',
        quantity: null,
        originWalletId: null,
        originWalletName: null,
        destinationWalletId: 'wallet-1',
        destinationWalletName: 'Detail Wallet',
      },
    ])

    const rows = wrapper.findAll('[data-testid="move-row"]')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('VALE3')
    expect(rows[0].text()).toContain('Saída')
    expect(rows[0].text()).toContain('Outra carteira')
    expect(rows[1].text()).toContain('Entrada')
    expect(rows[1].text()).toContain('Carteira removida')
    expect(rows[1].text()).toContain('Tudo')
  })

  it('shows an empty move history when nothing was moved', async () => {
    const { wrapper } = await mountView(detailOf())

    const emptyState = wrapper.find(
      '[data-testid="move-history"] [data-testid="move-history-empty"]',
    )
    expect(emptyState.text()).toContain('Nenhuma movimentação')
  })

  it('opens the move modal from the Ações dropdown with this wallet as the fixed origin', async () => {
    vi.mocked(holdingsApi.findAll).mockResolvedValue({
      content: [mockRow],
      page: { size: 500, number: 0, totalElements: 1, totalPages: 1 },
    })
    const { wrapper } = await mountView(detailOf())
    const walletsStore = useWalletsStore()
    const wallet: WalletResponse = {
      id: 'wallet-1',
      name: 'Detail Wallet',
      kind: 'STOCKS',
      currency: 'BRL',
      holdingCount: 1,
      totalInvested: 4500,
      currentValue: 4750,
      gain: 250,
      gainPct: 5.5556,
      createdAt: '2026-01-01T00:00:00Z',
    }
    walletsStore.wallets = [wallet]
    walletsStore.walletById = (id: string) => (id === wallet.id ? wallet : undefined)

    await wrapper.find('[data-testid="wallet-move"]').trigger('click')
    await flushPromises()
    await flushPromises()

    expect(wrapper.text()).toContain('Mover investimentos')
    expect(wrapper.find('[data-testid="move-origin"]').exists()).toBe(false)
    expect(holdingsApi.findAll).toHaveBeenCalledWith({ walletId: 'wallet-1', size: 500 })
    expect(wrapper.find('.move-item.is-selected').exists()).toBe(false)
  })

  it('keeps a closed holding row free of action buttons', async () => {
    const { wrapper } = await mountView(detailOf())

    const row = wrapper.find('tr.inv-row')
    expect(row.findAll('button')).toHaveLength(0)
    expect(row.find('.chev').exists()).toBe(true)
  })

  it('puts the investments and the move history in one tabbed card, investments first', async () => {
    const { wrapper, holdingsListStore, walletMovesStore } = await mountView(detailOf())
    holdingsListStore.totalElements = 7
    walletMovesStore.totalElements = 3
    await flushPromises()

    const card = wrapper.find('.table-card')
    const tabs = card.findAll('.tabs li')
    expect(tabs).toHaveLength(2)
    expect(tabs[0].text()).toMatch(/^Investimentos\s*7$/)
    expect(tabs[1].text()).toMatch(/^Movimentações\s*3$/)
    expect(tabs[0].classes()).toContain('is-active')
    expect(card.find('[data-testid="move-history"]').exists()).toBe(true)
    expect(card.find('table.inv-table').exists()).toBe(true)
  })

  it('switches to the move history tab', async () => {
    const { wrapper } = await mountView(detailOf())

    await wrapper.findAll('.tabs li a')[1].trigger('click')
    await flushPromises()

    const tabs = wrapper.findAll('.tabs li')
    expect(tabs[0].classes()).not.toContain('is-active')
    expect(tabs[1].classes()).toContain('is-active')
  })

  it('labels each delta chip with its period when there is history', async () => {
    const { wrapper } = await mountView(
      detailOf({
        series: [
          {
            snapshotDate: '2026-09-19',
            currentValue: 4750,
            totalInvested: 4500,
            gain: 250,
            gainPct: 5.5,
          },
        ],
        dayChange: 10,
        weekChange: 20,
        monthChange: 30,
      }),
    )

    const deltas = wrapper.find('[data-testid="wallet-deltas"]').text()
    expect(deltas).toContain('1 dia')
    expect(deltas).toContain('7 dias')
    expect(deltas).toContain('30 dias')
  })

  it('hides the delta chips when there is no history', async () => {
    const { wrapper } = await mountView(detailOf({ series: [] }))

    expect(wrapper.find('[data-testid="wallet-deltas"]').exists()).toBe(false)
  })

  it('loads the detail and the wallet-scoped holdings on mount', async () => {
    const { walletDetailStore, holdingsListStore } = await mountView(detailOf())

    expect(walletDetailStore.load).toHaveBeenCalledWith('wallet-1')
    expect(holdingsListStore.loadKind).toHaveBeenCalledWith('all', 0, {
      walletId: 'wallet-1',
      size: 10,
    })
  })

  it('shows the wallet header figures', async () => {
    const { wrapper } = await mountView(detailOf())

    expect(wrapper.text()).toContain('Detail Wallet')
    expect(wrapper.text()).toContain('BRL')
    expect(wrapper.text()).toContain('4.750,00')
    expect(wrapper.text()).toContain('4.500,00')
  })

  it('shows an empty state instead of a chart when there is no snapshot history', async () => {
    const { wrapper } = await mountView(detailOf({ series: [] }))

    expect(wrapper.text()).toContain('Ainda sem histórico')
  })

  it('renders the chart once a snapshot series exists', async () => {
    const { wrapper } = await mountView(
      detailOf({
        series: [
          {
            snapshotDate: '2026-09-19',
            currentValue: 4750,
            totalInvested: 4500,
            gain: 250,
            gainPct: 5.5,
          },
        ],
        dayChange: 0,
      }),
    )

    expect(wrapper.text()).not.toContain('Ainda sem histórico')
    expect(wrapper.find('canvas').exists()).toBe(true)
  })

  it('shows the largest position share in its highlight cell, with no separate warning banner', async () => {
    const { wrapper } = await mountView(
      detailOf({ largestHoldingName: 'Petrobras', largestHoldingShare: 81.05 }),
    )

    expect(wrapper.find('.message.is-warning').exists()).toBe(false)
    expect(wrapper.find('[data-testid="highlight-largest"]').text()).toContain('81,05%')
  })

  it('shows a dash in each highlight cell that has no data', async () => {
    const { wrapper } = await mountView(detailOf())

    for (const testId of ['highlight-best', 'highlight-worst', 'highlight-largest']) {
      expect(wrapper.find(`[data-testid="${testId}"]`).text()).toContain('—')
    }
  })

  it('lists the wallet holdings and expands a row into the detail panel', async () => {
    const { wrapper } = await mountView(detailOf())

    expect(wrapper.text()).toContain('PETR4')

    await wrapper.find('tr.inv-row').trigger('click')
    await flushPromises()

    expect(wrapper.find('tr.detail-row').exists()).toBe(true)
  })

  it('swaps a frozen holding square for the snowflake badge and dims its row in the investments table', async () => {
    const { wrapper } = await mountView(detailOf(), [
      { ...mockRow, frozen: true },
      { ...mockRow, id: 'holding-2', ticker: 'VALE3' },
    ])

    const rows = wrapper.findAll('tr.inv-row')
    const badge = rows[0].find('[data-testid="frozen-tag"]')
    expect(badge.attributes('title')).toBe('Congelado')
    expect(badge.attributes('aria-label')).toBe('Congelado')
    expect(badge.find('.mdi-snowflake').exists()).toBe(true)
    expect(rows[0].findAll('.ticker-badge')).toHaveLength(1)
      expect(rows[0].find('.ticker-badge').attributes('data-testid')).toBe('frozen-tag')
    expect(rows[0].find('.t-ticker').text()).toBe('PETR4')
    expect(rows[0].text()).not.toContain('Congelado')
    expect(rows[0].classes()).toContain('is-frozen')
    expect(rows[1].find('[data-testid="frozen-tag"]').exists()).toBe(false)
    expect(rows[1].find('.ticker-badge').exists()).toBe(true)
    expect(rows[1].classes()).not.toContain('is-frozen')
  })

  it('swaps a frozen fund square for the snowflake badge too', async () => {
    const { wrapper } = await mountView(detailOf(), [
      { ...mockRow, kind: 'FUNDS', ticker: 'Tesouro Selic', name: '', frozen: true },
    ])

    const row = wrapper.find('tr.inv-row')
    expect(row.find('[data-testid="frozen-tag"]').attributes('title')).toBe('Congelado')
    expect(row.findAll('.ticker-badge')).toHaveLength(1)
      expect(row.find('.ticker-badge').attributes('data-testid')).toBe('frozen-tag')
    expect(row.text()).not.toContain('Congelado')
    expect(row.classes()).toContain('is-frozen')
  })

  it('shows an empty state when the wallet has no investments', async () => {
    const { wrapper } = await mountView(detailOf(), [])

    expect(wrapper.text()).toContain('Nenhum investimento nesta carteira')
    expect(wrapper.find('table.inv-table').exists()).toBe(false)
  })

  it('goes back to the wallets list', async () => {
    const { wrapper, router } = await mountView(detailOf())

    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('wallets')
  })

  it('offers a rename button beside the wallet name', async () => {
    const { wrapper } = await mountView(detailOf())

    expect(wrapper.find('button[aria-label="Renomear carteira"]').exists()).toBe(true)
  })

  it('jumps to the holdings list filtered by this wallet', async () => {
    const { wrapper, router } = await mountView(detailOf())

    const item = wrapper
      .findAll('[data-testid="wallet-actions"] .dropdown-item')
      .find((element) => element.text() === 'Ver investimentos')
    await item!.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('investments')
    expect(router.currentRoute.value.query).toEqual({ filter: 'STOCKS', walletId: 'wallet-1' })
  })

  it('groups the wallet actions under one Ações dropdown instead of separate buttons', async () => {
    const { wrapper } = await mountView(detailOf(), [mockRow], true, true)

    const actions = wrapper.find('[data-testid="wallet-actions"]')
    expect(actions.find('button').text()).toBe('Ações')
    expect(actions.findAll('.dropdown-item').map((item) => item.text())).toEqual([
      'Ver investimentos',
      'Mover',
      'Reinvestir',
      'Remover',
    ])
    expect(wrapper.findAll('button').some((button) => button.text() === 'Mover')).toBe(false)
  })

  it('hides the remove action from a non-admin', async () => {
    const { wrapper } = await mountView(detailOf(), [mockRow], true, false)

    expect(wrapper.find('[data-testid="wallet-remove"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="wallet-reinvest"]').exists()).toBe(true)
  })

  it('opens the reinvest modal from the Ações dropdown with a source picker', async () => {
    vi.mocked(holdingsApi.findAll).mockResolvedValue({
      content: [mockRow],
      page: { size: 500, number: 0, totalElements: 1, totalPages: 1 },
    })
    const { wrapper } = await mountView(detailOf())

    await wrapper.find('[data-testid="wallet-reinvest"]').trigger('click')
    await flushPromises()
    await flushPromises()

    const source = control(wrapper, 'reinvest-source')
    expect((source.element as HTMLSelectElement).value).toBe('')
    expect(holdingsApi.findAll).toHaveBeenCalledWith({ walletId: 'wallet-1', size: 500 })
  })

  it('reloads the wallet after a reinvestment so a fully reinvested holding drops out', async () => {
    const reinvestedWholeRow: HoldingRow = { ...mockRow, id: 'holding-2', ticker: 'VALE3' }
    vi.mocked(holdingsApi.findAll).mockImplementation(async (params) => {
      const content = params.walletId ? [mockRow] : [mockRow, reinvestedWholeRow]
      return {
        content,
        page: { size: 500, number: 0, totalElements: content.length, totalPages: 1 },
      }
    })
    const { wrapper, walletDetailStore, holdingsListStore } = await mountView(detailOf())
    const walletsStore = useWalletsStore()
    const reinvestmentsStore = useReinvestmentsStore()
    vi.mocked(reinvestmentsStore.reinvest).mockResolvedValue(undefined)
    vi.mocked(holdingsListStore.loadKind).mockClear()
    vi.mocked(walletDetailStore.load).mockClear()

    await wrapper.find('[data-testid="wallet-reinvest"]').trigger('click')
    await flushPromises()
    await flushPromises()
    await control(wrapper, 'reinvest-source').setValue('holding-1')
    await control(wrapper, 'reinvest-destination').setValue('holding-2')
    await control(wrapper, 'reinvest-quantity').setValue('100')
    await wrapper.find('[data-testid="reinvest-submit"]').trigger('click')
    await flushPromises()

    expect(reinvestmentsStore.reinvest).toHaveBeenCalledTimes(1)
    expect(holdingsListStore.loadKind).toHaveBeenCalledWith('all', 0, {
      walletId: 'wallet-1',
      size: 10,
    })
    expect(walletDetailStore.load).toHaveBeenCalledWith('wallet-1')
    expect(walletsStore.refresh).toHaveBeenCalled()
    expect(wrapper.text()).not.toContain('Venda parte ou toda a posição e reinvista')
  })

  it('renders best, worst and largest position as a three-cell strip inside the header card', async () => {
    const { wrapper } = await mountView(
      detailOf({
        bestPerformer: {
          id: 'h1',
          name: 'Itau',
          ticker: 'ITUB4',
          kind: 'STOCKS',
          gain: 3475,
          gainPct: 39.71,
        },
        worstPerformer: {
          id: 'h2',
          name: 'TRX',
          ticker: 'TRXF11',
          kind: 'FUNDS',
          gain: -110,
          gainPct: -11.22,
        },
        largestHoldingName: 'Petrobras',
        largestHoldingShare: 41.1,
      }),
    )

    const strip = wrapper.find('.wd-highlight-strip')
    expect(strip.findAll('.wd-highlight-cell')).toHaveLength(3)
    expect(strip.text()).toContain('Melhor desempenho')
    expect(strip.text()).toContain('Pior desempenho')
    expect(strip.text()).toContain('Maior posição')

    const best = wrapper.find('[data-testid="highlight-best"]')
    expect(best.text()).toContain('ITUB4')
    expect(best.find('.has-text-success-on-scheme').text()).toBe('+39,71%')
    const worst = wrapper.find('[data-testid="highlight-worst"]')
    expect(worst.text()).toContain('TRXF11')
    expect(worst.find('.has-text-danger-on-scheme').text()).toBe('−11,22%')
    expect(wrapper.find('[data-testid="highlight-largest"]').text()).toContain('41,10%')
  })

  it('keeps every card free of the bottom margin that Bulma adds, so the page gap sets the spacing', async () => {
    const { wrapper } = await mountView(detailOf())

    const cards = wrapper.findAll('.page > .card')
    expect(cards.length).toBeGreaterThanOrEqual(4)
    expect(cards.every((card) => card.classes().includes('mb-0'))).toBe(true)
  })

  describe('loading overlays', () => {
    const move: WalletMoveRow = {
      id: 'move-out',
      movedAt: '2026-09-20',
      direction: 'OUT',
      kind: 'STOCKS',
      holdingName: 'Vale',
      ticker: 'VALE3',
      quantity: 10,
      originWalletId: 'wallet-1',
      originWalletName: 'Detail Wallet',
      destinationWalletId: 'wallet-2',
      destinationWalletName: 'Outra carteira',
    }

    it('shows the page overlay only while the wallet detail is loading', async () => {
      const { wrapper, walletDetailStore } = await mountView(detailOf())

      expect(wrapper.find('.loading-overlay').exists()).toBe(false)

      walletDetailStore.loading = true
      await flushPromises()
      expect(wrapper.findAll('.loading-overlay')).toHaveLength(1)

      walletDetailStore.loading = false
      await flushPromises()
      expect(wrapper.find('.loading-overlay').exists()).toBe(false)
    })

    it('shows the move history overlay only while the moves are loading', async () => {
      const { wrapper, walletMovesStore } = await mountView(detailOf(), [mockRow], true, true, [
        move,
      ])

      expect(wrapper.find('[data-testid="move-history"] .loading-overlay').exists()).toBe(false)

      walletMovesStore.loading = true
      await flushPromises()
      expect(wrapper.findAll('.loading-overlay')).toHaveLength(1)
      expect(wrapper.find('[data-testid="move-history"] .loading-overlay').exists()).toBe(true)

      walletMovesStore.loading = false
      await flushPromises()
      expect(wrapper.find('.loading-overlay').exists()).toBe(false)
    })

    it('shows the investments table overlay only while the holdings are loading', async () => {
      const { wrapper, holdingsListStore } = await mountView(detailOf())

      expect(wrapper.find('.table-card .loading-overlay').exists()).toBe(false)

      holdingsListStore.loading = true
      await flushPromises()
      expect(wrapper.findAll('.loading-overlay')).toHaveLength(1)
      expect(wrapper.find('.table-card .loading-overlay').exists()).toBe(true)

      holdingsListStore.loading = false
      await flushPromises()
      expect(wrapper.find('.loading-overlay').exists()).toBe(false)
    })
  })
})
