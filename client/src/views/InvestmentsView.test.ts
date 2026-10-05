import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createMemoryHistory, createRouter } from 'vue-router'
import InvestmentsView from './InvestmentsView.vue'
import { holdingsApi } from '@/api/holdings'
import { useAuthStore } from '@/stores/auth'
import { useCurrencyStore } from '@/stores/currency'
import { useHoldingsListStore } from '@/stores/holdingsList'
import { useRatesStore } from '@/stores/rates'
import { useTypesListStore } from '@/stores/typesList'
import { useWalletsStore } from '@/stores/wallets'
import { ModalKey } from '@/composables/useModals'
import type { AssetType, HoldingRow, StockHoldingDetail, WalletResponse } from '@/types'

vi.mock('@/api/holdings', () => ({
  holdingsApi: {
    findAll: vi.fn(),
    getStockHolding: vi.fn(),
    getCryptoHolding: vi.fn(),
    getFundHolding: vi.fn(),
    deleteStockLot: vi.fn(),
    deleteStockHolding: vi.fn(),
    updateStockHolding: vi.fn(),
  },
}))
vi.mock('@/api/results', () => ({ resultsApi: {} }))
vi.mock('@/api/reinvestments', () => ({ reinvestmentsApi: { findAll: vi.fn(), reinvest: vi.fn() } }))
vi.mock('@/api/walletMoves', () => ({ walletMovesApi: { findAll: vi.fn(), move: vi.fn() } }))

const stockRow: HoldingRow = {
  id: 'holding-1',
  kind: 'STOCKS',
  name: 'Petróleo Brasileiro',
  ticker: 'PETR4',
  typeLabel: 'Ação PN',
  walletId: 'wallet-1',
  walletName: 'Carteira B3',
  walletCurrency: 'BRL',
  quantity: 200,
  costBasis: 5652,
  currentPrice: 34.8,
  currentValue: 6960,
  gain: 1308,
  gainPct: 23.1,
  frozen: false,
}

const cryptoRow: HoldingRow = {
  id: 'holding-2',
  kind: 'CRYPTO',
  name: 'Bitcoin',
  ticker: 'BTC',
  typeLabel: null,
  walletId: 'wallet-2',
  walletName: 'Carteira Cripto',
  walletCurrency: 'BRL',
  quantity: 0.5,
  costBasis: 100000,
  currentPrice: 180000,
  currentValue: 90000,
  gain: -10000,
  gainPct: -10,
  frozen: false,
}

const fundRow: HoldingRow = {
  id: 'holding-3',
  kind: 'FUNDS',
  name: 'Tesouro IPCA+',
  ticker: null,
  typeLabel: 'Renda Fixa',
  walletId: 'wallet-3',
  walletName: 'Carteira Fundos',
  walletCurrency: 'BRL',
  quantity: null,
  costBasis: 3000,
  currentPrice: null,
  currentValue: 3300,
  gain: 300,
  gainPct: 10,
  frozen: false,
}

const unpricedRow: HoldingRow = {
  ...stockRow,
  id: 'holding-4',
  ticker: 'VALE3',
  name: 'Vale',
  typeLabel: null,
  quantity: 0,
  currentPrice: null,
  currentValue: null,
  gain: null,
  gainPct: null,
}

const stockDetail: StockHoldingDetail = {
  id: 'holding-1',
  walletId: 'wallet-1',
  stockTypeId: 'type-1',
  ticker: 'PETR4',
  name: 'Petróleo Brasileiro',
  currentPrice: 34.8,
  lots: [{ id: 'lot-1', lotDate: '2026-01-12', quantity: 200, price: 28.26 }],
  frozen: false,
  withdrawals: [],
}

function walletOf(id: string, name: string, kind: WalletResponse['kind']): WalletResponse {
  return {
    id,
    name,
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

const wallets = [
  walletOf('wallet-1', 'Carteira B3', 'STOCKS'),
  walletOf('wallet-2', 'Carteira Cripto', 'CRYPTO'),
  walletOf('wallet-3', 'Carteira Fundos', 'FUNDS'),
]

const stockTypes: AssetType[] = [
  { id: 'type-1', name: 'Ação PN', usageCount: 1 },
  { id: 'type-2', name: 'FII', usageCount: 2 },
]

const fundTypes: AssetType[] = [{ id: 'type-3', name: 'Renda Fixa', usageCount: 1 }]

interface MountOptions {
  query?: string
  rows?: HoldingRow[]
  loaded?: boolean
  loading?: boolean
  totalPages?: number
  totalElements?: number
  page?: number
  pageSize?: number
  isAdmin?: boolean
}

let activeWrapper: VueWrapper | undefined

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

async function mountView(options: MountOptions = {}) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/investments', name: 'investments', component: { template: '<div />' } },
      {
        path: '/investments/report',
        name: 'investments-report',
        component: { template: '<div />' },
      },
    ],
  })
  router.push(`/investments${options.query ?? ''}`)
  await router.isReady()

  const pinia = createTestingPinia()
  vi.mocked(useCurrencyStore().convert).mockImplementation((amount) => amount)
  useAuthStore().session = {
    name: 'Admin',
    email: 'admin@admin.com',
    role: options.isAdmin === false ? 'USER' : 'ADMIN',
    status: 'APPROVED',
    authProvider: 'LOCAL',
    demoModeEnabled: false,
  }
  useWalletsStore().wallets = wallets
  useTypesListStore().stockTypes = stockTypes
  useTypesListStore().fundTypes = fundTypes
  const holdingsListStore = useHoldingsListStore()
  holdingsListStore.rows = options.rows ?? [stockRow, cryptoRow, fundRow]
  holdingsListStore.loaded = options.loaded ?? true
  holdingsListStore.loading = options.loading ?? false
  holdingsListStore.totalPages = options.totalPages ?? 1
  holdingsListStore.totalElements = options.totalElements ?? holdingsListStore.rows.length
  holdingsListStore.page = options.page ?? 0
  holdingsListStore.pageSize = options.pageSize ?? 20

  const modals = {
    openAddInvestment: vi.fn(),
    openCreateWallet: vi.fn(),
    openPasswordChange: vi.fn(),
    openTrustedDevices: vi.fn(),
  }

  const wrapper = mount(InvestmentsView, {
    global: { plugins: [pinia, router], provide: { [ModalKey as symbol]: modals } },
    attachTo: document.body,
  })
  activeWrapper = wrapper
  await flushPromises()
  const initialLoad = [...vi.mocked(holdingsListStore.loadKind).mock.calls]
  vi.mocked(holdingsListStore.loadKind).mockClear()
  return { wrapper, router, modals, holdingsListStore, initialLoad }
}

function tabLabels(wrapper: VueWrapper) {
  return wrapper.findAll('.seg-tab').map((tab) => tab.text())
}

function activeTab(wrapper: VueWrapper) {
  return wrapper.find('.seg-tab.active').text()
}

function tab(wrapper: VueWrapper, label: string) {
  return wrapper.findAll('.seg-tab').find((candidate) => candidate.text() === label)!
}

function headerCell(wrapper: VueWrapper, label: string) {
  return wrapper.findAll('thead th').find((cell) => cell.text() === label)!
}

function bodyRows(wrapper: VueWrapper) {
  return wrapper.findAll('tbody tr.inv-row')
}

function selectOptions(select: ReturnType<VueWrapper['find']>) {
  return select.findAll('option').map((option) => option.text())
}

function selects(wrapper: VueWrapper) {
  return wrapper.findAll('.inv-toolbar select')
}

describe('InvestmentsView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
    activeWrapper?.unmount()
    activeWrapper = undefined
  })

  describe('loading', () => {
    it('loads the lookup stores and the first unfiltered page when it mounts', async () => {
      const { initialLoad } = await mountView()

      expect(useTypesListStore().load).toHaveBeenCalledTimes(1)
      expect(useWalletsStore().load).toHaveBeenCalledTimes(1)
      expect(useCurrencyStore().load).toHaveBeenCalledTimes(1)
      expect(useRatesStore().load).toHaveBeenCalledTimes(1)
      expect(initialLoad).toEqual([
        ['all', 0, { typeLabel: undefined, walletId: undefined, search: undefined, sort: undefined }],
      ])
    })

    it('restores filter, wallet, type, search, sort and page from the URL', async () => {
      const { wrapper, initialLoad } = await mountView({
        query: '?filter=STOCKS&walletId=wallet-1&type=FII&search=petr&sort=gain,asc&page=3',
      })

      expect(initialLoad).toEqual([
        ['STOCKS', 2, { typeLabel: 'FII', walletId: 'wallet-1', search: 'petr', sort: 'gain,asc' }],
      ])
      expect(activeTab(wrapper)).toBe('Ações')
      expect(wrapper.find<HTMLInputElement>('.search-input input').element.value).toBe('petr')
    })

    it('ignores an unknown filter, an unknown sort key and a non-positive page in the URL', async () => {
      const { wrapper, initialLoad } = await mountView({
        query: '?filter=BONDS&sort=colour,asc&page=0',
      })

      expect(initialLoad).toEqual([
        ['all', 0, { typeLabel: undefined, walletId: undefined, search: undefined, sort: undefined }],
      ])
      expect(activeTab(wrapper)).toBe('Todos')
    })

    it('defaults an unrecognised sort direction to descending', async () => {
      const { initialLoad } = await mountView({ query: '?sort=gain,sideways' })

      expect(initialLoad[0][2]).toEqual(expect.objectContaining({ sort: 'gain,desc' }))
    })
  })

  describe('rows', () => {
    it('renders one row per holding with its ticker, kind tag, wallet and name', async () => {
      const { wrapper } = await mountView()

      const rows = bodyRows(wrapper)
      expect(rows).toHaveLength(3)
      expect(rows[0].text()).toContain('PETR4')
      expect(rows[0].text()).toContain('Ação PN')
      expect(rows[0].text()).toContain('Petróleo Brasileiro')
      expect(rows[0].text()).toContain('Carteira B3')
      expect(rows[1].text()).toContain('BTC')
      expect(rows[1].text()).toContain('Cripto')
      expect(rows[1].text()).toContain('Carteira Cripto')
    })

    it('shows quantity, current price, average price, invested, current value and result of a stock', async () => {
      const { wrapper } = await mountView({ rows: [stockRow] })

      const text = bodyRows(wrapper)[0].text()
      expect(text).toContain('200')
      expect(text).toContain('R$ 34,80')
      expect(text).toContain('PM R$ 28,26')
      expect(text).toContain('R$ 5.652,00')
      expect(text).toContain('R$ 6.960,00')
      expect(text).toContain('+R$ 1.308,00')
      expect(text).toContain('+23,10%')
    })

    it('shows a loss as a negative result and a fractional quantity in full', async () => {
      const { wrapper } = await mountView({ rows: [cryptoRow] })

      const text = bodyRows(wrapper)[0].text()
      expect(text).toContain('0,5')
      expect(text).toContain('−R$ 10.000,00')
      expect(text).toContain('−10,00%')
    })

    it('shows a fund by its name, its current value as the price and no quantity or average price', async () => {
      const { wrapper } = await mountView({ rows: [fundRow] })

      const row = bodyRows(wrapper)[0]
      expect(row.find('.t-ticker').text()).toBe('Tesouro IPCA+')
      expect(row.find('.type-tag').text()).toBe('Renda Fixa')
      expect(row.find('.t-name').exists()).toBe(false)
      expect(row.text()).toContain('R$ 3.300,00')
      expect(row.text()).not.toContain('PM')
      expect(row.findAll('td')[2].text()).toBe('—')
    })

    it('falls back to dashes for a holding without price, value or result', async () => {
      const { wrapper } = await mountView({ rows: [unpricedRow] })

      const cells = bodyRows(wrapper)[0].findAll('td')
      expect(cells[2].text()).toBe('0')
      expect(cells[3].text()).toBe('—')
      expect(cells[5].text()).toBe('—')
      expect(cells[6].text()).toBe('—')
    })

    it('swaps a frozen stock square for the snowflake badge and dims the row, leaving open ones as they were', async () => {
      const { wrapper } = await mountView({ rows: [{ ...stockRow, frozen: true }, cryptoRow] })

      const rows = bodyRows(wrapper)
      const badge = rows[0].find('[data-testid="frozen-tag"]')
      expect(badge.attributes('title')).toBe('Congelado')
      expect(badge.attributes('aria-label')).toBe('Congelado')
      expect(badge.find('.mdi-snowflake').exists()).toBe(true)
      expect(rows[0].find('.ticker-badge').exists()).toBe(false)
      expect(rows[0].find('.t-ticker').text()).toBe('PETR4')
      expect(rows[0].text()).not.toContain('Congelado')
      expect(rows[0].classes()).toContain('is-frozen')
      expect(rows[1].find('[data-testid="frozen-tag"]').exists()).toBe(false)
      expect(rows[1].find('.ticker-badge').exists()).toBe(true)
      expect(rows[1].classes()).not.toContain('is-frozen')
      expect(wrapper.find('tr.detail-row').exists()).toBe(false)
    })

    it('swaps a frozen fund square for the snowflake badge too', async () => {
      const { wrapper } = await mountView({ rows: [{ ...fundRow, frozen: true }] })

      const row = bodyRows(wrapper)[0]
      expect(row.find('[data-testid="frozen-tag"]').attributes('title')).toBe('Congelado')
      expect(row.find('[data-testid="frozen-tag"] .mdi-snowflake').exists()).toBe(true)
      expect(row.find('.ticker-badge').exists()).toBe(false)
      expect(row.text()).not.toContain('Congelado')
      expect(row.classes()).toContain('is-frozen')
    })
    it('labels a fund and a stock without a type with a generic tag', async () => {
      const { wrapper } = await mountView({
        rows: [
          { ...fundRow, id: 'f', typeLabel: null },
          { ...stockRow, id: 's', typeLabel: null },
        ],
      })

      const tags = wrapper.findAll('.type-tag').map((tag) => tag.text())
      expect(tags).toEqual(['Fundo', 'Ação'])
    })

  })

  describe('empty state', () => {
    it('replaces the table with an empty state once loading finished with no rows', async () => {
      const { wrapper } = await mountView({ rows: [] })

      expect(wrapper.text()).toContain('Nenhum investimento aqui')
      expect(wrapper.text()).toContain('Registre uma aquisição para vê-la no seu logbook.')
      expect(wrapper.find('table.inv-table').exists()).toBe(false)
    })

    it('keeps the table while the first load is still pending', async () => {
      const { wrapper } = await mountView({ rows: [], loaded: false })

      expect(wrapper.text()).not.toContain('Nenhum investimento aqui')
      expect(wrapper.find('table.inv-table').exists()).toBe(true)
    })

    it('keeps the table while a reload is in flight', async () => {
      const { wrapper } = await mountView({ rows: [], loading: true })

      expect(wrapper.text()).not.toContain('Nenhum investimento aqui')
      expect(wrapper.find('table.inv-table').exists()).toBe(true)
    })

    it('offers to add an investment from the empty state', async () => {
      const { wrapper, modals } = await mountView({ rows: [] })

      const addButton = wrapper
        .findAll('.empty button')
        .find((button) => button.text() === 'Adicionar investimento')!
      await addButton.trigger('click')

      expect(modals.openAddInvestment).toHaveBeenCalledWith(undefined)
    })
  })

  describe('tabs', () => {
    it('lists the four kind tabs with Todos active by default', async () => {
      const { wrapper } = await mountView()

      expect(tabLabels(wrapper)).toEqual(['Todos', 'Ações', 'Cripto', 'Fundos'])
      expect(activeTab(wrapper)).toBe('Todos')
    })

    it('reloads the first page of the chosen kind and writes it to the URL', async () => {
      const { wrapper, router, holdingsListStore } = await mountView({ query: '?page=4' })

      await tab(wrapper, 'Cripto').trigger('click')
      await flushPromises()

      expect(holdingsListStore.loadKind).toHaveBeenCalledTimes(1)
      expect(holdingsListStore.loadKind).toHaveBeenCalledWith('CRYPTO', 0, {
        typeLabel: undefined,
        walletId: undefined,
        search: undefined,
        sort: undefined,
      })
      expect(activeTab(wrapper)).toBe('Cripto')
      expect(router.currentRoute.value.query).toEqual({ filter: 'CRYPTO' })
    })

    it('does nothing when the active tab is clicked again', async () => {
      const { wrapper, holdingsListStore } = await mountView()

      await tab(wrapper, 'Todos').trigger('click')

      expect(holdingsListStore.loadKind).not.toHaveBeenCalled()
    })

    it('drops the wallet, type and search filters when the kind changes', async () => {
      const { wrapper, router, holdingsListStore } = await mountView({
        query: '?filter=STOCKS&walletId=wallet-1&type=FII&search=petr',
      })

      await tab(wrapper, 'Fundos').trigger('click')
      await flushPromises()

      expect(holdingsListStore.loadKind).toHaveBeenCalledWith('FUNDS', 0, {
        typeLabel: undefined,
        walletId: undefined,
        search: undefined,
        sort: undefined,
      })
      expect(router.currentRoute.value.query).toEqual({ filter: 'FUNDS' })
      expect(wrapper.find<HTMLInputElement>('.search-input input').element.value).toBe('')
    })

    it('keeps the chosen sort when the kind changes', async () => {
      const { wrapper, holdingsListStore } = await mountView({ query: '?sort=gain,desc' })

      await tab(wrapper, 'Ações').trigger('click')
      await flushPromises()

      expect(holdingsListStore.loadKind).toHaveBeenCalledWith('STOCKS', 0, {
        typeLabel: undefined,
        walletId: undefined,
        search: undefined,
        sort: 'gain,desc',
      })
    })
  })

  describe('filters', () => {
    it('lists every wallet on the Todos tab, labelled with its kind', async () => {
      const { wrapper } = await mountView()

      expect(selectOptions(selects(wrapper)[0])).toEqual([
        'Todas as carteiras',
        '[Ações] Carteira B3',
        '[Cripto] Carteira Cripto',
        '[Fundos] Carteira Fundos',
      ])
    })

    it('narrows the wallet list to the active kind', async () => {
      const { wrapper } = await mountView({ query: '?filter=CRYPTO' })

      expect(selectOptions(selects(wrapper)[0])).toEqual([
        'Todas as carteiras',
        '[Cripto] Carteira Cripto',
      ])
    })

    it('reloads the first page for the chosen wallet', async () => {
      const { wrapper, router, holdingsListStore } = await mountView({ query: '?page=3' })

      await selects(wrapper)[0].setValue('wallet-2')
      await flushPromises()

      expect(holdingsListStore.loadKind).toHaveBeenCalledWith('all', 0, {
        typeLabel: undefined,
        walletId: 'wallet-2',
        search: undefined,
        sort: undefined,
      })
      expect(router.currentRoute.value.query).toEqual({ walletId: 'wallet-2' })
    })

    it('clears the wallet filter when all wallets are chosen again', async () => {
      const { wrapper, holdingsListStore } = await mountView({
        query: '?walletId=wallet-2',
      })

      await selects(wrapper)[0].setValue('')
      await flushPromises()

      expect(holdingsListStore.loadKind).toHaveBeenCalledWith('all', 0, {
        typeLabel: undefined,
        walletId: undefined,
        search: undefined,
        sort: undefined,
      })
    })

    it('offers no type filter on the Todos and Cripto tabs', async () => {
      const { wrapper } = await mountView()

      expect(selects(wrapper)).toHaveLength(1)

      await tab(wrapper, 'Cripto').trigger('click')
      await flushPromises()

      expect(selects(wrapper)).toHaveLength(1)
    })

    it('offers the stock types on the Ações tab and filters by the chosen one', async () => {
      const { wrapper, router, holdingsListStore } = await mountView({ query: '?filter=STOCKS' })

      const typeSelect = selects(wrapper)[1]
      expect(selectOptions(typeSelect)).toEqual(['Todos os tipos', 'Ação PN', 'FII'])

      await typeSelect.setValue('FII')
      await flushPromises()

      expect(holdingsListStore.loadKind).toHaveBeenCalledWith('STOCKS', 0, {
        typeLabel: 'FII',
        walletId: undefined,
        search: undefined,
        sort: undefined,
      })
      expect(router.currentRoute.value.query).toEqual({ filter: 'STOCKS', type: 'FII' })
    })

    it('offers the fund types on the Fundos tab', async () => {
      const { wrapper } = await mountView({ query: '?filter=FUNDS' })

      expect(selectOptions(selects(wrapper)[1])).toEqual(['Todos os tipos', 'Renda Fixa'])
    })

    it('clears the type filter when all types are chosen again', async () => {
      const { wrapper, holdingsListStore } = await mountView({ query: '?filter=STOCKS&type=FII' })

      await selects(wrapper)[1].setValue('')
      await flushPromises()

      expect(holdingsListStore.loadKind).toHaveBeenCalledWith('STOCKS', 0, {
        typeLabel: undefined,
        walletId: undefined,
        search: undefined,
        sort: undefined,
      })
    })

    it('hides the wallet filter while the user has no wallets', async () => {
      const { wrapper } = await mountView()
      useWalletsStore().wallets = []
      await flushPromises()

      expect(selects(wrapper)).toHaveLength(0)
    })
  })

  describe('search', () => {
    it('waits for the typing to pause, then reloads the first page with the trimmed term', async () => {
      const { wrapper, router, holdingsListStore } = await mountView({ query: '?page=2' })
      vi.useFakeTimers()

      await wrapper.find('.search-input input').setValue('  petr  ')
      vi.advanceTimersByTime(299)
      expect(holdingsListStore.loadKind).not.toHaveBeenCalled()

      vi.advanceTimersByTime(1)
      vi.useRealTimers()
      await flushPromises()

      expect(holdingsListStore.loadKind).toHaveBeenCalledTimes(1)
      expect(holdingsListStore.loadKind).toHaveBeenCalledWith('all', 0, {
        typeLabel: undefined,
        walletId: undefined,
        search: 'petr',
        sort: undefined,
      })
      expect(router.currentRoute.value.query).toEqual({ search: 'petr' })
    })

    it('restarts the pause when the term keeps changing', async () => {
      const { wrapper, holdingsListStore } = await mountView()
      vi.useFakeTimers()

      await wrapper.find('.search-input input').setValue('pe')
      vi.advanceTimersByTime(200)
      await wrapper.find('.search-input input').setValue('petr')
      vi.advanceTimersByTime(200)
      expect(holdingsListStore.loadKind).not.toHaveBeenCalled()

      vi.advanceTimersByTime(100)
      vi.useRealTimers()
      await flushPromises()

      expect(holdingsListStore.loadKind).toHaveBeenCalledTimes(1)
      expect(holdingsListStore.loadKind).toHaveBeenCalledWith(
        'all',
        0,
        expect.objectContaining({ search: 'petr' }),
      )
    })

    it('clears the search from the request when the term is blanked', async () => {
      const { wrapper, router, holdingsListStore } = await mountView({ query: '?search=petr' })
      vi.useFakeTimers()

      await wrapper.find('.search-input input').setValue('   ')
      vi.advanceTimersByTime(300)
      vi.useRealTimers()
      await flushPromises()

      expect(holdingsListStore.loadKind).toHaveBeenCalledWith(
        'all',
        0,
        expect.objectContaining({ search: undefined }),
      )
      expect(router.currentRoute.value.query).toEqual({})
    })
  })

  describe('sorting', () => {
    it('sorts ascending on the first click of a column and reloads from the first page', async () => {
      const { wrapper, router, holdingsListStore } = await mountView({ query: '?page=2' })

      await headerCell(wrapper, 'Preço atual').trigger('click')
      await flushPromises()

      expect(holdingsListStore.loadKind).toHaveBeenCalledWith('all', 0, {
        typeLabel: undefined,
        walletId: undefined,
        search: undefined,
        sort: 'price,asc',
      })
      expect(router.currentRoute.value.query).toEqual({ sort: 'price,asc' })
    })

    it('flips the direction when the same column is clicked again', async () => {
      const { wrapper, holdingsListStore } = await mountView()

      await headerCell(wrapper, 'Investido').trigger('click')
      await headerCell(wrapper, 'Investido').trigger('click')
      await flushPromises()

      expect(holdingsListStore.loadKind).toHaveBeenLastCalledWith(
        'all',
        0,
        expect.objectContaining({ sort: 'invested,desc' }),
      )
    })

    it('starts ascending again on a different column', async () => {
      const { wrapper, holdingsListStore } = await mountView({ query: '?sort=gain,desc' })

      await headerCell(wrapper, 'Carteira').trigger('click')
      await flushPromises()

      expect(holdingsListStore.loadKind).toHaveBeenLastCalledWith(
        'all',
        0,
        expect.objectContaining({ sort: 'wallet,asc' }),
      )
    })

    it('marks the sorted column header as active', async () => {
      const { wrapper } = await mountView({ query: '?sort=current,asc' })

      expect(headerCell(wrapper, 'Valor atual').find('.sort-icon-active').exists()).toBe(true)
      expect(headerCell(wrapper, 'Resultado').find('.sort-icon-active').exists()).toBe(false)
    })
  })

  describe('pagination', () => {
    it('shows no pager when everything fits on one page', async () => {
      const { wrapper } = await mountView({ totalPages: 1 })

      expect(wrapper.find('.table-foot').exists()).toBe(false)
      expect(wrapper.find('.pagination').exists()).toBe(false)
    })

    it('shows a pager when the store reports more than one page', async () => {
      const { wrapper } = await mountView({
        totalPages: 3,
        totalElements: 45,
        page: 1,
        pageSize: 20,
      })

      expect(wrapper.find('.table-foot .pagination').exists()).toBe(true)
    })

    it('loads the next page, keeping the filters, when the pager moves forward', async () => {
      const { wrapper, holdingsListStore } = await mountView({
        query: '?filter=STOCKS&search=petr&sort=gain,asc',
        totalPages: 3,
        totalElements: 45,
        page: 0,
        pageSize: 20,
      })

      await wrapper.find('.pagination-next').trigger('click')
      await flushPromises()

      expect(holdingsListStore.loadKind).toHaveBeenCalledWith('STOCKS', 1, {
        typeLabel: undefined,
        walletId: undefined,
        search: 'petr',
        sort: 'gain,asc',
      })
    })

    it('writes the one-based page into the URL', async () => {
      const { wrapper, router } = await mountView({
        totalPages: 3,
        totalElements: 45,
        page: 0,
        pageSize: 20,
      })

      await wrapper.find('.pagination-next').trigger('click')
      await flushPromises()

      expect(router.currentRoute.value.query).toEqual({ page: '2' })
    })

    it('loads the previous page when the pager moves back', async () => {
      const { wrapper, holdingsListStore } = await mountView({
        query: '?page=3',
        totalPages: 3,
        totalElements: 45,
        page: 2,
        pageSize: 20,
      })

      await wrapper.find('.pagination-previous').trigger('click')
      await flushPromises()

      expect(holdingsListStore.loadKind).toHaveBeenCalledWith('all', 1, expect.any(Object))
    })

    it('collapses the open row when the page changes', async () => {
      vi.mocked(holdingsApi.getStockHolding).mockResolvedValue(stockDetail)
      const { wrapper } = await mountView({
        rows: [stockRow],
        totalPages: 2,
        totalElements: 25,
        page: 0,
        pageSize: 20,
      })
      await bodyRows(wrapper)[0].trigger('click')
      await flushPromises()
      expect(wrapper.find('tr.detail-row').exists()).toBe(true)

      await wrapper.find('.pagination-next').trigger('click')
      await flushPromises()

      expect(wrapper.find('tr.detail-row').exists()).toBe(false)
    })
  })

  describe('routing', () => {
    it('re-hydrates and reloads when the URL changes from outside the view', async () => {
      const { wrapper, router, holdingsListStore } = await mountView()

      await router.push('/investments?filter=FUNDS&walletId=wallet-3&page=2')
      await flushPromises()

      expect(holdingsListStore.loadKind).toHaveBeenCalledTimes(1)
      expect(holdingsListStore.loadKind).toHaveBeenCalledWith('FUNDS', 1, {
        typeLabel: undefined,
        walletId: 'wallet-3',
        search: undefined,
        sort: undefined,
      })
      expect(activeTab(wrapper)).toBe('Fundos')
    })

    it('collapses the open row on an external navigation', async () => {
      vi.mocked(holdingsApi.getStockHolding).mockResolvedValue(stockDetail)
      const { wrapper, router } = await mountView({ rows: [stockRow] })
      await bodyRows(wrapper)[0].trigger('click')
      await flushPromises()

      await router.push('/investments?filter=STOCKS')
      await flushPromises()

      expect(wrapper.find('tr.detail-row').exists()).toBe(false)
    })

    it('does not reload a second time for the URL it wrote itself', async () => {
      const { wrapper, holdingsListStore } = await mountView()

      await tab(wrapper, 'Ações').trigger('click')
      await flushPromises()
      await headerCell(wrapper, 'Valor atual').trigger('click')
      await flushPromises()

      expect(holdingsListStore.loadKind).toHaveBeenCalledTimes(2)
    })
  })

  describe('row expansion', () => {
    it('expands a row into the holding detail panel and fetches that holding', async () => {
      vi.mocked(holdingsApi.getStockHolding).mockResolvedValue(stockDetail)
      const { wrapper } = await mountView()

      await bodyRows(wrapper)[0].trigger('click')
      await flushPromises()

      expect(wrapper.findAll('tr.detail-row')).toHaveLength(1)
      expect(bodyRows(wrapper)[0].classes()).toContain('is-open')
      expect(holdingsApi.getStockHolding).toHaveBeenCalledTimes(1)
      expect(holdingsApi.getStockHolding).toHaveBeenCalledWith('wallet-1', 'holding-1')
      expect(wrapper.find('tr.detail-row').text()).toContain('Movimentações')
    })

    it('fetches a crypto and a fund holding through their own endpoints', async () => {
      vi.mocked(holdingsApi.getCryptoHolding).mockResolvedValue({
        id: 'holding-2',
        walletId: 'wallet-2',
        ticker: 'BTC',
        name: 'Bitcoin',
        currentPrice: 180000,
        lots: [],
        frozen: false,
        withdrawals: [],
      })
      vi.mocked(holdingsApi.getFundHolding).mockResolvedValue({
        id: 'holding-3',
        walletId: 'wallet-3',
        fundTypeId: 'type-3',
        name: 'Tesouro IPCA+',
        currentValue: 3300,
        administrationFeeRate: null,
        performanceFeeRate: null,
        contributions: [],
        frozen: false,
        withdrawals: [],
      })
      const { wrapper } = await mountView()

      await bodyRows(wrapper)[1].trigger('click')
      await flushPromises()
      expect(holdingsApi.getCryptoHolding).toHaveBeenCalledWith('wallet-2', 'holding-2')

      await bodyRows(wrapper)[2].trigger('click')
      await flushPromises()
      expect(holdingsApi.getFundHolding).toHaveBeenCalledWith('wallet-3', 'holding-3')
    })

    it('flips the chevron while a row is open', async () => {
      vi.mocked(holdingsApi.getStockHolding).mockResolvedValue(stockDetail)
      const { wrapper } = await mountView()
      const chevron = () => bodyRows(wrapper)[0].find('.chev .mdi')

      expect(chevron().classes()).toContain('mdi-chevron-down')

      await bodyRows(wrapper)[0].trigger('click')

      expect(chevron().classes()).toContain('mdi-chevron-up')
    })

    it('collapses the panel when the open row is clicked again', async () => {
      vi.mocked(holdingsApi.getStockHolding).mockResolvedValue(stockDetail)
      const { wrapper } = await mountView()

      await bodyRows(wrapper)[0].trigger('click')
      await flushPromises()
      await bodyRows(wrapper)[0].trigger('click')

      expect(wrapper.find('tr.detail-row').exists()).toBe(false)
      expect(bodyRows(wrapper)[0].classes()).not.toContain('is-open')
    })

    it('keeps a single row open at a time', async () => {
      vi.mocked(holdingsApi.getStockHolding).mockResolvedValue(stockDetail)
      vi.mocked(holdingsApi.getFundHolding).mockResolvedValue({
        id: 'holding-3',
        walletId: 'wallet-3',
        fundTypeId: 'type-3',
        name: 'Tesouro IPCA+',
        currentValue: 3300,
        administrationFeeRate: null,
        performanceFeeRate: null,
        contributions: [],
        frozen: false,
        withdrawals: [],
      })
      const { wrapper } = await mountView()

      await bodyRows(wrapper)[0].trigger('click')
      await flushPromises()
      await bodyRows(wrapper)[2].trigger('click')
      await flushPromises()

      expect(wrapper.findAll('tr.detail-row')).toHaveLength(1)
      expect(bodyRows(wrapper)[0].classes()).not.toContain('is-open')
      expect(bodyRows(wrapper)[2].classes()).toContain('is-open')
    })

    it('collapses the open row when the kind tab changes', async () => {
      vi.mocked(holdingsApi.getStockHolding).mockResolvedValue(stockDetail)
      const { wrapper } = await mountView()
      await bodyRows(wrapper)[0].trigger('click')
      await flushPromises()

      await tab(wrapper, 'Ações').trigger('click')
      await flushPromises()

      expect(wrapper.find('tr.detail-row').exists()).toBe(false)
    })

    it('collapses the open row when a wallet or type filter changes', async () => {
      vi.mocked(holdingsApi.getStockHolding).mockResolvedValue(stockDetail)
      const { wrapper } = await mountView({ query: '?filter=STOCKS' })
      await bodyRows(wrapper)[0].trigger('click')
      await flushPromises()
      expect(wrapper.find('tr.detail-row').exists()).toBe(true)

      await selects(wrapper)[1].setValue('FII')
      await flushPromises()
      expect(wrapper.find('tr.detail-row').exists()).toBe(false)

      await bodyRows(wrapper)[0].trigger('click')
      await flushPromises()
      await selects(wrapper)[0].setValue('wallet-1')
      await flushPromises()
      expect(wrapper.find('tr.detail-row').exists()).toBe(false)
    })
  })

  describe('reacting to the detail panel', () => {
    async function openStockPanel() {
      vi.mocked(holdingsApi.getStockHolding).mockResolvedValue(stockDetail)
      const context = await mountView({ rows: [stockRow] })
      await bodyRows(context.wrapper)[0].trigger('click')
      await flushPromises()
      return context
    }

    function dialogButton(label: string) {
      return Array.from(document.body.querySelectorAll('.modal.is-active button')).find(
        (button) => button.textContent?.trim() === label,
      ) as HTMLButtonElement
    }

    it('collapses the panel and refreshes the list after the holding is removed', async () => {
      vi.mocked(holdingsApi.deleteStockHolding).mockResolvedValue(undefined)
      const { wrapper, holdingsListStore } = await openStockPanel()

      document.body
        .querySelector<HTMLElement>('[data-testid="holding-remove"]')!
        .dispatchEvent(new MouseEvent('click', { bubbles: true }))
      await flushPromises()
      dialogButton('Remover').click()
      await flushPromises()

      expect(holdingsApi.deleteStockHolding).toHaveBeenCalledWith('wallet-1', 'holding-1')
      expect(holdingsListStore.refresh).toHaveBeenCalledTimes(1)
      expect(wrapper.find('tr.detail-row').exists()).toBe(false)
    })

    it('freezes the holding from the panel and shows the new state once the list reloads', async () => {
      vi.mocked(holdingsApi.updateStockHolding).mockResolvedValue({ ...stockDetail, frozen: true })
      const { wrapper, holdingsListStore } = await openStockPanel()
      vi.mocked(holdingsListStore.refresh).mockImplementation(async () => {
        holdingsListStore.rows = [{ ...stockRow, frozen: true }]
      })
      expect(wrapper.find('[data-testid="frozen-tag"]').exists()).toBe(false)

      document.body
        .querySelector<HTMLElement>('[data-testid="holding-freeze"]')!
        .dispatchEvent(new MouseEvent('click', { bubbles: true }))
      await flushPromises()

      expect(holdingsApi.updateStockHolding).toHaveBeenCalledWith('wallet-1', 'holding-1', {
        frozen: true,
      })
      expect(holdingsListStore.refresh).toHaveBeenCalledTimes(1)
      expect(wrapper.find('tr.detail-row').exists()).toBe(true)
      expect(wrapper.findAll('[data-testid="frozen-tag"]')).toHaveLength(2)
      expect(document.body.querySelector('[data-testid="holding-freeze"]')!.textContent).toContain(
        'Descongelar',
      )
    })
    it('refreshes the list but keeps the panel open after a position changes', async () => {
      vi.mocked(holdingsApi.deleteStockLot).mockResolvedValue(undefined)
      const { wrapper, holdingsListStore } = await openStockPanel()

      await wrapper.find('tr.detail-row td.c-act button').trigger('click')
      await flushPromises()
      dialogButton('Remover').click()
      await flushPromises()

      expect(holdingsApi.deleteStockLot).toHaveBeenCalledWith('wallet-1', 'holding-1', 'lot-1')
      expect(holdingsListStore.refresh).toHaveBeenCalledTimes(1)
      expect(wrapper.find('tr.detail-row').exists()).toBe(true)
    })
  })

  describe('toolbar', () => {
    it('opens the add-investment modal without a kind on the Todos tab', async () => {
      const { wrapper, modals } = await mountView()

      const addButton = wrapper
        .findAll('.inv-toolbar button')
        .find((button) => button.text() === 'Adicionar investimento')!
      await addButton.trigger('click')

      expect(modals.openAddInvestment).toHaveBeenCalledWith(undefined)
    })

    it('opens the add-investment modal pre-set to the active kind', async () => {
      const { wrapper, modals } = await mountView({ query: '?filter=FUNDS' })

      const addButton = wrapper
        .findAll('.inv-toolbar button')
        .find((button) => button.text() === 'Adicionar investimento')!
      await addButton.trigger('click')

      expect(modals.openAddInvestment).toHaveBeenCalledWith('FUNDS')
    })

    it('opens the report in a new tab carrying the active filters but not the sort or page', async () => {
      const openWindow = vi.spyOn(window, 'open').mockReturnValue(null)
      const { wrapper } = await mountView({
        query: '?filter=STOCKS&walletId=wallet-1&type=FII&search=petr&sort=gain,asc&page=2',
      })

      await wrapper.find('button[aria-label="Exportar relatório"]').trigger('click')

      expect(openWindow).toHaveBeenCalledTimes(1)
      const [href, target] = openWindow.mock.calls[0]
      expect(target).toBe('_blank')
      expect(String(href)).toBe(
        '/investments/report?filter=STOCKS&walletId=wallet-1&type=FII&search=petr',
      )
    })

    it('opens an unfiltered report when no filter is active', async () => {
      const openWindow = vi.spyOn(window, 'open').mockReturnValue(null)
      const { wrapper } = await mountView()

      await wrapper.find('button[aria-label="Exportar relatório"]').trigger('click')

      expect(String(openWindow.mock.calls[0][0])).toBe('/investments/report')
    })
  })

  describe('loading overlay', () => {
    it('covers the table while a page is loading and leaves it once loaded', async () => {
      const { wrapper, holdingsListStore } = await mountView({ loading: true })

      expect(wrapper.findAll('.loading-overlay')).toHaveLength(1)

      holdingsListStore.loading = false
      await flushPromises()

      expect(wrapper.find('.loading-overlay').exists()).toBe(false)
    })

    it('shows no overlay when nothing is loading', async () => {
      const { wrapper } = await mountView()

      expect(wrapper.find('.loading-overlay').exists()).toBe(false)
    })
  })
})
