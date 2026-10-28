import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createRouter, createMemoryHistory } from 'vue-router'
import WalletDetailView from './WalletDetailView.vue'
import HoldingsTable from '@/components/investments/HoldingsTable.vue'
import TransferCashModal from '@/components/wallets/TransferCashModal.vue'
import WithdrawCashModal from '@/components/wallets/WithdrawCashModal.vue'
import { useWalletDetailStore } from '@/stores/walletDetail'
import { useHoldingsListStore } from '@/stores/holdingsList'
import { useAuthStore } from '@/stores/auth'
import { useWalletLedgerStore } from '@/stores/walletLedger'
import { useWalletsStore } from '@/stores/wallets'
import { useReinvestmentsStore } from '@/stores/reinvestments'
import { holdingsApi } from '@/api/holdings'
import type { HoldingRow, WalletDetail, WalletLedgerEntry, WalletResponse } from '@/types'
import { expectDialogShowsLiterally } from '@/test/expectDialogShowsLiterally'

vi.mock('@/api/walletMoves', () => ({ walletMovesApi: { move: vi.fn() } }))
vi.mock('@/api/walletLedger', () => ({ walletLedgerApi: { findAll: vi.fn() } }))
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
    cash: 0,
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
  segmentLabel: null,
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

function headerCell(wrapper: ReturnType<typeof mount>, label: string) {
  return wrapper.findAll('thead th').find((cell) => cell.text() === label)!
}

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

async function mountView(
  detail: WalletDetail | null,
  rows: HoldingRow[] = [mockRow],
  loaded = true,
  isAdmin = true,
  ledgerEntries: WalletLedgerEntry[] = [],
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
  const walletLedgerStore = useWalletLedgerStore()
  walletLedgerStore.rows = ledgerEntries
  walletLedgerStore.loaded = true

  await flushPromises()
  return { wrapper, router, walletDetailStore, holdingsListStore, walletLedgerStore }
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

  it('offers the by-segment grouping on a stock wallet only', async () => {
    vi.mocked(holdingsApi.findAllForReport).mockResolvedValue([mockRow])
    const groupingControl =
      '[data-testid="allocation-card"] [data-testid="allocation-grouping-segment"]'

    const stockWallet = await mountView(detailOf())
    expect(stockWallet.wrapper.find(groupingControl).exists()).toBe(true)
    stockWallet.wrapper.unmount()

    const cryptoWallet = await mountView(detailOf({ kind: 'CRYPTO' }))
    expect(cryptoWallet.wrapper.find(groupingControl).exists()).toBe(false)
  })

  it('shows the allocation empty state for a wallet with no holdings', async () => {
    const { wrapper } = await mountView(detailOf())

    expect(wrapper.find('[data-testid="allocation-card"]').text()).toContain(
      'Nenhum investimento nesta carteira',
    )
    expect(wrapper.find('canvas').exists()).toBe(false)
  })

  it('loads the wallet ledger on mount', async () => {
    const { walletLedgerStore } = await mountView(detailOf())

    expect(walletLedgerStore.load).toHaveBeenCalledWith('wallet-1', 0)
  })

  it('lists every event of the wallet in its own currency', async () => {
    const { wrapper } = await mountView(detailOf(), [mockRow], true, true, [
      {
        id: 'result-1',
        date: '2026-09-21',
        type: 'WITHDRAWAL',
        kind: 'STOCKS',
        holdingId: 'holding-1',
        holdingName: 'Itaú',
        ticker: 'ITUB4',
        quantity: 5,
        amount: 150,
        profit: 20,
        counterpartName: null,
        originalAmount: null,
        originalCurrency: null,
        rate: null,
        cashUsed: null,
        newMoney: null,
        cashBalance: 0,
      },
      {
        id: 'move-out',
        date: '2026-09-20',
        type: 'MOVE_OUT',
        kind: 'STOCKS',
        holdingId: 'holding-2',
        holdingName: 'Vale',
        ticker: 'VALE3',
        quantity: null,
        amount: null,
        profit: null,
        counterpartName: 'Outra carteira',
        originalAmount: null,
        originalCurrency: null,
        rate: null,
        cashUsed: null,
        newMoney: null,
        cashBalance: 0,
      },
    ])

    const rows = wrapper.findAll('[data-testid="ledger-row"]')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('ITUB4')
    expect(rows[0].text()).toContain('Resgate')
    expect(rows[0].get('[data-testid="ledger-amount"]').text()).toContain('150,00')
    expect(rows[1].text()).toContain('Saída')
    expect(rows[1].text()).toContain('Outra carteira')
    expect(rows[1].text()).toContain('Tudo')
  })

  it('shows an empty ledger when nothing was recorded', async () => {
    const { wrapper } = await mountView(detailOf())

    const emptyState = wrapper.find(
      '[data-testid="wallet-ledger"] [data-testid="wallet-ledger-empty"]',
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
      cash: 0,
      cashReconciled: true,
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

  it('gives each holding row the drawer button and the buy and price buttons, with no wallet column', async () => {
    const { wrapper } = await mountView(detailOf())

    const row = wrapper.find('tr.inv-row')
    expect(row.get('button.open-holding').text()).toBe('PETR4')
    expect(row.find('[data-testid="row-add-position"]').exists()).toBe(true)
    expect(row.find('[data-testid="row-update-price"]').exists()).toBe(true)
    expect(row.find('.wallet-ref').exists()).toBe(false)
    expect(row.find('.chev').exists()).toBe(false)
  })

  it('puts the investments and the wallet ledger in one tabbed card, investments first', async () => {
    const { wrapper, holdingsListStore, walletLedgerStore } = await mountView(detailOf())
    holdingsListStore.totalElements = 7
    walletLedgerStore.totalElements = 3
    await flushPromises()

    const card = wrapper.find('.table-card')
    const tabs = card.findAll('.tabs li')
    expect(tabs).toHaveLength(2)
    expect(tabs[0].text()).toMatch(/^Investimentos\s*7$/)
    expect(tabs[1].text()).toMatch(/^Movimentações\s*3$/)
    expect(tabs[0].classes()).toContain('is-active')
    expect(card.find('[data-testid="wallet-ledger"]').exists()).toBe(true)
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
      sort: 'gainPct,desc',
    })
  })

  it('sorts the investments by Ativo, Preço atual, Valor atual and Resultado, highest result first', async () => {
    const { wrapper } = await mountView(detailOf())

    expect(wrapper.findAll('thead th.sort-th').map((cell) => cell.text())).toEqual([
      'Ativo',
      'Preço atual',
      'Valor atual',
      'Resultado',
    ])
    expect(headerCell(wrapper, 'Resultado').find('.sort-icon-active').exists()).toBe(true)
  })

  it('reloads the first page with the toggled sort when a header is clicked', async () => {
    const { wrapper, holdingsListStore } = await mountView(detailOf())
    vi.mocked(holdingsListStore.loadKind).mockClear()

    await headerCell(wrapper, 'Ativo').trigger('click')
    await headerCell(wrapper, 'Ativo').trigger('click')

    expect(vi.mocked(holdingsListStore.loadKind).mock.calls).toEqual([
      ['all', 0, { walletId: 'wallet-1', size: 10, sort: 'asset,asc' }],
      ['all', 0, { walletId: 'wallet-1', size: 10, sort: 'asset,desc' }],
    ])
    expect(headerCell(wrapper, 'Ativo').find('.sort-icon-active').exists()).toBe(true)
  })

  it('keeps the chosen sort when paging', async () => {
    const { wrapper, holdingsListStore } = await mountView(detailOf())
    await headerCell(wrapper, 'Valor atual').trigger('click')
    vi.mocked(holdingsListStore.loadKind).mockClear()

    wrapper.findComponent(HoldingsTable).vm.$emit('page-change', 2)

    expect(holdingsListStore.loadKind).toHaveBeenCalledWith('all', 2, {
      walletId: 'wallet-1',
      size: 10,
      sort: 'current,asc',
    })
  })

  it('keeps the chosen sort when a purchase is added or a holding is relocated', async () => {
    const { wrapper, holdingsListStore } = await mountView(detailOf())
    await headerCell(wrapper, 'Ativo').trigger('click')
    vi.mocked(holdingsListStore.loadKind).mockClear()

    wrapper.findComponent(HoldingsTable).vm.$emit('position-added')
    wrapper.findComponent(HoldingsTable).vm.$emit('relocated')
    await flushPromises()

    expect(vi.mocked(holdingsListStore.loadKind).mock.calls).toEqual([
      ['all', 0, { walletId: 'wallet-1', size: 10, sort: 'asset,asc' }],
      ['all', 0, { walletId: 'wallet-1', size: 10, sort: 'asset,asc' }],
    ])
  })

  it('closes the holding drawer when the sort changes', async () => {
    const { wrapper } = await mountView(detailOf())
    await wrapper.find('tr.inv-row').trigger('click')
    await flushPromises()

    await headerCell(wrapper, 'Preço atual').trigger('click')
    await flushPromises()

    expect(document.body.querySelector('[data-testid="holding-drawer"]')).toBeNull()
  })

  it('ignores a sort key it does not know', async () => {
    const { wrapper, holdingsListStore } = await mountView(detailOf())
    vi.mocked(holdingsListStore.loadKind).mockClear()

    wrapper.findComponent(HoldingsTable).vm.$emit('sort', 'colour')

    expect(holdingsListStore.loadKind).not.toHaveBeenCalled()
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

  it('lists the wallet holdings and opens a row in the holding drawer', async () => {
    const { wrapper } = await mountView(detailOf())

    expect(wrapper.text()).toContain('PETR4')

    await wrapper.find('tr.inv-row').trigger('click')
    await flushPromises()

    expect(document.body.querySelector('[data-testid="holding-drawer"]')).not.toBeNull()
    expect(wrapper.find('tr.inv-row').classes()).toContain('is-current')
  })

  it('closes the holding drawer when the wallet reloads', async () => {
    const { wrapper, router } = await mountView(detailOf())
    await wrapper.find('tr.inv-row').trigger('click')
    await flushPromises()

    await router.push('/wallets/wallet-2')
    await flushPromises()

    expect(document.body.querySelector('[data-testid="holding-drawer"]')).toBeNull()
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

  it('shows a wallet name containing markup literally in the remove confirmation', async () => {
    const markupName = '<img src=x onerror=alert(1)>'
    const { wrapper } = await mountView(detailOf({ name: markupName }))

    await wrapper.find('[data-testid="wallet-remove"]').trigger('click')
    await flushPromises()

    expectDialogShowsLiterally(markupName)
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
      sort: 'gainPct,desc',
    })
    expect(walletDetailStore.load).toHaveBeenCalledWith('wallet-1')
    expect(walletsStore.refresh).toHaveBeenCalled()
    expect(wrapper.text()).not.toContain('Venda parte ou toda a posição e reinvista')
  })

  it('opens the transfer from the header and reloads the wallet once it is recorded', async () => {
    const { wrapper, walletDetailStore } = await mountView(detailOf({ cash: 300 }))
    vi.mocked(walletDetailStore.load).mockClear()

    await wrapper.get('[data-testid="wallet-transfer-cash"]').trigger('click')
    await flushPromises()
    const modal = wrapper.findComponent(TransferCashModal)
    expect(modal.props()).toMatchObject({ walletId: 'wallet-1', currency: 'BRL', cash: 300 })

    modal.vm.$emit('transferred')
    modal.vm.$emit('close')
    await flushPromises()

    expect(walletDetailStore.load).toHaveBeenCalledWith('wallet-1')
    expect(wrapper.findComponent(TransferCashModal).exists()).toBe(false)
  })

  it('pins the cash row in the investments table and opens both dialogs from it', async () => {
    const { wrapper, walletDetailStore } = await mountView(detailOf({ cash: 300 }))
    vi.mocked(walletDetailStore.load).mockClear()

    expect(wrapper.get('[data-testid="cash-row-value"]').text()).toBe('R$ 300,00')
    await wrapper.get('[data-testid="cash-row-transfer"]').trigger('click')
    await flushPromises()
    expect(wrapper.findComponent(TransferCashModal).exists()).toBe(true)

    await wrapper.get('[data-testid="cash-row-withdraw"]').trigger('click')
    await flushPromises()
    const modal = wrapper.findComponent(WithdrawCashModal)
    modal.vm.$emit('withdrawn')
    modal.vm.$emit('close')
    await flushPromises()

    expect(walletDetailStore.load).toHaveBeenCalledWith('wallet-1')
    expect(wrapper.findComponent(WithdrawCashModal).exists()).toBe(false)
  })

  it('opens the bank withdrawal from the header', async () => {
    const { wrapper } = await mountView(detailOf({ cash: 300 }))

    await wrapper.get('[data-testid="wallet-withdraw-cash"]').trigger('click')
    await flushPromises()

    expect(wrapper.findComponent(WithdrawCashModal).props('walletName')).toBe('Detail Wallet')
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
    const ledgerEntry: WalletLedgerEntry = {
      id: 'move-out',
      date: '2026-09-20',
      type: 'MOVE_OUT',
      kind: 'STOCKS',
      holdingId: 'holding-2',
      holdingName: 'Vale',
      ticker: 'VALE3',
      quantity: 10,
      amount: null,
      profit: null,
      counterpartName: 'Outra carteira',
      originalAmount: null,
      originalCurrency: null,
      rate: null,
      cashUsed: null,
      newMoney: null,
      cashBalance: 0,
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

    it('shows the ledger overlay only while the ledger is loading', async () => {
      const { wrapper, walletLedgerStore } = await mountView(detailOf(), [mockRow], true, true, [
        ledgerEntry,
      ])

      expect(wrapper.find('[data-testid="wallet-ledger"] .loading-overlay').exists()).toBe(false)

      walletLedgerStore.loading = true
      await flushPromises()
      expect(wrapper.findAll('.loading-overlay')).toHaveLength(1)
      expect(wrapper.find('[data-testid="wallet-ledger"] .loading-overlay').exists()).toBe(true)

      walletLedgerStore.loading = false
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
