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

  it('shows the move history above the investments listing', async () => {
    const { wrapper } = await mountView(detailOf())

    const html = wrapper.html()
    expect(html.indexOf('data-testid="move-history"')).toBeGreaterThan(-1)
    expect(html.indexOf('data-testid="move-history"')).toBeLessThan(html.indexOf('inv-table'))
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

  it('shows the largest position share in its card, with no separate warning banner', async () => {
    const { wrapper } = await mountView(
      detailOf({ largestHoldingName: 'Petrobras', largestHoldingShare: 81.05 }),
    )

    expect(wrapper.find('.message.is-warning').exists()).toBe(false)
    expect(wrapper.find('.wd-share-pct').text()).toBe('81,05%')
  })

  it('lists the wallet holdings and expands a row into the detail panel', async () => {
    const { wrapper } = await mountView(detailOf())

    expect(wrapper.text()).toContain('PETR4')

    await wrapper.find('tr.inv-row').trigger('click')
    await flushPromises()

    expect(wrapper.find('tr.detail-row').exists()).toBe(true)
  })

  it('tags a frozen holding as Congelado in the investments table', async () => {
    const { wrapper } = await mountView(detailOf(), [
      { ...mockRow, frozen: true },
      { ...mockRow, id: 'holding-2', ticker: 'VALE3' },
    ])

    const rows = wrapper.findAll('tr.inv-row')
    expect(rows[0].find('[data-testid="frozen-tag"]').text()).toBe('Congelado')
    expect(rows[1].find('[data-testid="frozen-tag"]').exists()).toBe(false)
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

  it('renders best, worst and largest position as three highlight cards', async () => {
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

    expect(wrapper.findAll('.wd-highlight')).toHaveLength(3)
    expect(wrapper.text()).toContain('Melhor desempenho')
    expect(wrapper.text()).toContain('Pior desempenho')
    expect(wrapper.text()).toContain('Maior posição')
    expect(wrapper.text()).toContain('ITUB4')
    expect(wrapper.text()).toContain('TRXF11')
    expect(wrapper.find('.wd-share-pct').text()).toBe('41,10%')
  })
})
