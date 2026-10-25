import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createMemoryHistory, createRouter } from 'vue-router'
import InvestmentsView from './InvestmentsView.vue'
import { holdingsApi } from '@/api/holdings'
import { useAuthStore } from '@/stores/auth'
import { useCurrencyStore } from '@/stores/currency'
import { useHoldingsListStore } from '@/stores/holdingsList'
import { useOverviewStore } from '@/stores/overview'
import { useRatesStore } from '@/stores/rates'
import { useTypesListStore } from '@/stores/typesList'
import { useWalletsStore } from '@/stores/wallets'
import { ModalKey } from '@/composables/useModals'
import type {
  HoldingType,
  HoldingRow,
  KindSummary,
  StockHoldingDetail,
  WalletResponse,
} from '@/types'

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
vi.mock('@/api/reinvestments', () => ({
  reinvestmentsApi: { findAll: vi.fn(), reinvest: vi.fn() },
}))
vi.mock('@/api/walletMoves', () => ({ walletMovesApi: { findAll: vi.fn(), move: vi.fn() } }))

const stockRow: HoldingRow = {
  id: 'holding-1',
  kind: 'STOCKS',
  name: 'Petróleo Brasileiro',
  ticker: 'PETR4',
  typeLabel: 'Ação PN',
  segmentLabel: null,
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
  segmentLabel: null,
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
  segmentLabel: null,
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
  typeId: 'type-1',
  typeName: 'Ação PN',
  segmentId: null,
  segmentName: null,
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

const holdingTypes: HoldingType[] = [
  { id: 'type-1', name: 'Ação PN', kinds: ['STOCKS'], usageCount: 1 },
  { id: 'type-2', name: 'FII', kinds: ['STOCKS'], usageCount: 2 },
  { id: 'type-3', name: 'Renda Fixa', kinds: ['FUNDS'], usageCount: 1 },
  { id: 'type-4', name: 'Moeda', kinds: ['CRYPTO'], usageCount: 1 },
  { id: 'type-5', name: 'Token', kinds: ['CRYPTO'], usageCount: 0 },
]

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
  useTypesListStore().holdingTypes = holdingTypes
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

function kindSummaryOf(kind: string, holdingCount: number): KindSummary {
  return {
    kind,
    holdingCount,
    totalCostBasis: 0,
    totalCurrentValue: 0,
    totalGain: 0,
    totalGainPct: null,
  }
}

function classLabels(wrapper: VueWrapper) {
  return wrapper
    .findAll('fieldset')[0]
    .findAll('.rail-label')
    .map((label) => label.text())
}

function classRadio(wrapper: VueWrapper, filter: string) {
  return wrapper.get<HTMLInputElement>(`[data-testid="class-${filter}"]`)
}

function activeClass(wrapper: VueWrapper) {
  return wrapper.get<HTMLInputElement>('input[name="investments-class"]:checked').element.value
}

function walletLabels(wrapper: VueWrapper) {
  return wrapper
    .findAll('fieldset')[1]
    .findAll('.rail-label')
    .map((label) => label.text())
}

function walletCheckbox(wrapper: VueWrapper, walletId: string) {
  return wrapper.get<HTMLInputElement>(`[data-testid="wallet-${walletId}"]`)
}

function typeLabels(wrapper: VueWrapper) {
  return wrapper
    .findAll('fieldset')[2]
    .findAll('.rail-label')
    .map((label) => label.text())
}

function typeCheckbox(wrapper: VueWrapper, typeLabel: string) {
  return wrapper.get<HTMLInputElement>(`[data-testid="type-${typeLabel}"]`)
}

function hideFrozenCheckbox(wrapper: VueWrapper) {
  return wrapper.get<HTMLInputElement>('[data-testid="hide-frozen"]')
}

function searchInput(wrapper: VueWrapper) {
  return wrapper.get<HTMLInputElement>('.investments-search input')
}

function chipLabels(wrapper: VueWrapper) {
  return wrapper.findAll('.investments-main-head .tag').map((chip) => chip.text())
}

function headerButton(wrapper: VueWrapper, label: string) {
  return wrapper.findAll('.head-actions button').find((button) => button.text() === label)!
}

function exportButton(wrapper: VueWrapper) {
  return headerButton(wrapper, 'Exportar relatório')
}

function headerCell(wrapper: VueWrapper, label: string) {
  return wrapper.findAll('thead th').find((cell) => cell.text() === label)!
}

function bodyRows(wrapper: VueWrapper) {
  return wrapper.findAll('tbody tr.inv-row')
}

function drawerOpen() {
  return document.body.querySelector('[data-testid="holding-drawer"]') !== null
}

function drawerText() {
  return document.body.querySelector('[data-testid="holding-drawer"]')?.textContent ?? ''
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
        [
          'all',
          0,
          { typeLabel: undefined, walletId: undefined, search: undefined, sort: 'gainPct,desc' },
        ],
      ])
    })

    it('restores filter, wallet, type, search, sort and page from the URL', async () => {
      const { wrapper, initialLoad } = await mountView({
        query: '?filter=STOCKS&walletId=wallet-1&type=FII&search=petr&sort=gain,asc&page=3',
      })

      expect(initialLoad).toEqual([
        [
          'STOCKS',
          2,
          { typeLabel: ['FII'], walletId: ['wallet-1'], search: 'petr', sort: 'gain,asc' },
        ],
      ])
      expect(activeClass(wrapper)).toBe('STOCKS')
      expect(searchInput(wrapper).element.value).toBe('petr')
    })

    it('ignores an unknown filter, an unknown sort key and a non-positive page in the URL', async () => {
      const { wrapper, initialLoad } = await mountView({
        query: '?filter=BONDS&sort=colour,asc&page=0',
      })

      expect(initialLoad).toEqual([
        [
          'all',
          0,
          { typeLabel: undefined, walletId: undefined, search: undefined, sort: 'gainPct,desc' },
        ],
      ])
      expect(activeClass(wrapper)).toBe('all')
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

    it('shows quantity, average price, current price, current value and result of a stock, without the invested amount', async () => {
      const { wrapper } = await mountView({ rows: [stockRow] })

      const cells = bodyRows(wrapper)[0]
        .findAll('td.c-num')
        .map((cell) => cell.text())
      expect(cells[0]).toBe('200')
      expect(cells[1]).toBe('R$ 28,26')
      expect(cells[2]).toBe('R$ 34,80')
      expect(cells[3]).toBe('R$ 6.960,00')
      expect(cells[4]).toContain('+R$ 1.308,00')
      expect(cells[4]).toContain('+23,10%')
      expect(bodyRows(wrapper)[0].text()).not.toContain('R$ 5.652,00')
    })

    it('shows a loss as a negative result and a fractional quantity in full', async () => {
      const { wrapper } = await mountView({ rows: [cryptoRow] })

      const text = bodyRows(wrapper)[0].text()
      expect(text).toContain('0,5')
      expect(text).toContain('−R$ 10.000,00')
      expect(text).toContain('−10,00%')
    })

    it('shows a fund by its name, with its current value and dashes for quantity and prices', async () => {
      const { wrapper } = await mountView({ rows: [fundRow] })

      const row = bodyRows(wrapper)[0]
      expect(row.find('.t-ticker').text()).toBe('Tesouro IPCA+')
      expect(row.find('.type-tag').text()).toBe('Renda Fixa')
      expect(row.find('.t-name').text()).toBe('Fundo de investimento')
      const cells = row.findAll('td.c-num').map((cell) => cell.text())
      expect(cells.slice(0, 3)).toEqual(['—', '—', '—'])
      expect(cells[3]).toBe('R$ 3.300,00')
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
      expect(rows[0].findAll('.ticker-badge')).toHaveLength(1)
      expect(rows[0].find('.ticker-badge').attributes('data-testid')).toBe('frozen-tag')
      expect(rows[0].find('.t-ticker').text()).toBe('PETR4')
      expect(rows[0].text()).not.toContain('Congelado')
      expect(rows[0].classes()).toContain('is-frozen')
      expect(rows[1].find('[data-testid="frozen-tag"]').exists()).toBe(false)
      expect(rows[1].find('.ticker-badge').exists()).toBe(true)
      expect(rows[1].classes()).not.toContain('is-frozen')
      expect(drawerOpen()).toBe(false)
    })

    it('swaps a frozen fund square for the snowflake badge too', async () => {
      const { wrapper } = await mountView({ rows: [{ ...fundRow, frozen: true }] })

      const row = bodyRows(wrapper)[0]
      expect(row.find('[data-testid="frozen-tag"]').attributes('title')).toBe('Congelado')
      expect(row.find('[data-testid="frozen-tag"] .mdi-snowflake').exists()).toBe(true)
      expect(row.findAll('.ticker-badge')).toHaveLength(1)
      expect(row.find('.ticker-badge').attributes('data-testid')).toBe('frozen-tag')
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

      expect(modals.openAddInvestment).toHaveBeenCalledWith(undefined, undefined)
    })
  })

  describe('header', () => {
    it('summarises positions, wallets and the total value under the title', async () => {
      const { wrapper } = await mountView()
      useOverviewStore().summary = {
        displayCurrency: 'BRL',
        totalCostBasis: 100000,
        totalCurrentValue: 123456.78,
        totalGain: 23456.78,
        totalGainPct: 23.45,
        kindSummaries: [
          kindSummaryOf('STOCKS', 9),
          kindSummaryOf('CRYPTO', 3),
          kindSummaryOf('FUNDS', 4),
        ],
      }
      await flushPromises()

      expect(wrapper.get('.page-desc').text()).toBe(
        '16 posições em 3 carteiras · R$ 123.456,78 no total',
      )
    })

    it('refreshes the summary when it mounts and after the holdings change', async () => {
      const { wrapper } = await mountView({ rows: [stockRow] })
      expect(useOverviewStore().refresh).toHaveBeenCalledTimes(1)

      wrapper.getComponent({ name: 'HoldingsTable' }).vm.$emit('position-added')
      await flushPromises()

      expect(useOverviewStore().refresh).toHaveBeenCalledTimes(2)
      expect(useHoldingsListStore().refresh).toHaveBeenCalledTimes(1)
    })

    it('shows no summary line before the overview has loaded', async () => {
      const { wrapper } = await mountView()

      expect(wrapper.find('.page-desc').exists()).toBe(false)
    })
  })

  describe('class', () => {
    it('lists the four classes with Todas as classes selected by default', async () => {
      const { wrapper } = await mountView()

      expect(classLabels(wrapper)).toEqual(['Todas as classes', 'Ações', 'Cripto', 'Fundos'])
      expect(activeClass(wrapper)).toBe('all')
    })

    it('counts the holdings of each class from the overview summary', async () => {
      const { wrapper } = await mountView()
      useOverviewStore().summary = {
        displayCurrency: 'BRL',
        totalCostBasis: 0,
        totalCurrentValue: 0,
        totalGain: 0,
        totalGainPct: null,
        kindSummaries: [kindSummaryOf('STOCKS', 9), kindSummaryOf('FUNDS', 4)],
      }
      await flushPromises()

      expect(
        wrapper
          .findAll('fieldset')[0]
          .findAll('.rail-side')
          .map((count) => count.text()),
      ).toEqual(['13', '9', '4'])
    })

    it('reloads the first page of the chosen class and writes it to the URL', async () => {
      const { wrapper, router, holdingsListStore } = await mountView({ query: '?page=4' })

      await classRadio(wrapper, 'CRYPTO').trigger('change')
      await flushPromises()

      expect(holdingsListStore.loadKind).toHaveBeenCalledTimes(1)
      expect(holdingsListStore.loadKind).toHaveBeenCalledWith('CRYPTO', 0, {
        typeLabel: undefined,
        walletId: undefined,
        search: undefined,
        sort: 'gainPct,desc',
      })
      expect(activeClass(wrapper)).toBe('CRYPTO')
      expect(router.currentRoute.value.query).toEqual({ filter: 'CRYPTO' })
    })

    it('keeps the search and the wallets that belong to the new class', async () => {
      const { wrapper, router, holdingsListStore } = await mountView({
        query: '?walletId=wallet-1&walletId=wallet-3&search=petr',
      })

      await classRadio(wrapper, 'FUNDS').trigger('change')
      await flushPromises()

      expect(holdingsListStore.loadKind).toHaveBeenCalledWith('FUNDS', 0, {
        typeLabel: undefined,
        walletId: ['wallet-3'],
        search: 'petr',
        sort: 'gainPct,desc',
      })
      expect(router.currentRoute.value.query).toEqual({
        filter: 'FUNDS',
        walletId: ['wallet-3'],
        search: 'petr',
      })
      expect(searchInput(wrapper).element.value).toBe('petr')
    })

    it('drops the asset types when the class changes', async () => {
      const { wrapper, holdingsListStore } = await mountView({
        query: '?filter=STOCKS&type=FII',
      })

      await classRadio(wrapper, 'all').trigger('change')
      await flushPromises()

      expect(holdingsListStore.loadKind).toHaveBeenCalledWith(
        'all',
        0,
        expect.objectContaining({ typeLabel: undefined }),
      )
    })

    it('keeps the chosen sort when the class changes', async () => {
      const { wrapper, holdingsListStore } = await mountView({ query: '?sort=gain,desc' })

      await classRadio(wrapper, 'STOCKS').trigger('change')
      await flushPromises()

      expect(holdingsListStore.loadKind).toHaveBeenCalledWith(
        'STOCKS',
        0,
        expect.objectContaining({ sort: 'gain,desc' }),
      )
    })
  })

  describe('wallets and types', () => {
    it('lists every wallet without a kind prefix under Todas as classes, with its holding count', async () => {
      const { wrapper } = await mountView()

      expect(walletLabels(wrapper)).toEqual(['Carteira B3', 'Carteira Cripto', 'Carteira Fundos'])
      expect(
        wrapper
          .findAll('fieldset')[1]
          .findAll('.rail-side')
          .map((count) => count.text()),
      ).toEqual(['1', '1', '1'])
    })

    it('narrows the wallet list to the active class', async () => {
      const { wrapper } = await mountView({ query: '?filter=CRYPTO' })

      expect(walletLabels(wrapper)).toEqual(['Carteira Cripto'])
    })

    it('filters by several wallets at once and unticks one again', async () => {
      const { wrapper, router, holdingsListStore } = await mountView({ query: '?page=3' })

      await walletCheckbox(wrapper, 'wallet-1').setValue(true)
      await walletCheckbox(wrapper, 'wallet-2').setValue(true)
      await flushPromises()

      expect(holdingsListStore.loadKind).toHaveBeenLastCalledWith('all', 0, {
        typeLabel: undefined,
        walletId: ['wallet-1', 'wallet-2'],
        search: undefined,
        sort: 'gainPct,desc',
      })
      expect(router.currentRoute.value.query).toEqual({ walletId: ['wallet-1', 'wallet-2'] })

      await walletCheckbox(wrapper, 'wallet-1').setValue(false)
      await flushPromises()

      expect(router.currentRoute.value.query).toEqual({ walletId: ['wallet-2'] })
    })

    it('ticks the wallets restored from the URL', async () => {
      const { wrapper } = await mountView({ query: '?walletId=wallet-1&walletId=wallet-3' })

      expect(walletCheckbox(wrapper, 'wallet-1').element.checked).toBe(true)
      expect(walletCheckbox(wrapper, 'wallet-2').element.checked).toBe(false)
      expect(walletCheckbox(wrapper, 'wallet-3').element.checked).toBe(true)
    })

    it('offers no asset type under Todas as classes and says why', async () => {
      const { wrapper } = await mountView()

      expect(typeLabels(wrapper)).toEqual([])
      expect(wrapper.get('[data-testid="type-hint"]').text()).toBe(
        'Escolha uma classe para filtrar por tipo.',
      )
    })

    it('offers the crypto types under Cripto and the fund types under Fundos', async () => {
      const { wrapper } = await mountView()

      await classRadio(wrapper, 'CRYPTO').trigger('change')
      await flushPromises()
      expect(typeLabels(wrapper)).toEqual(['Moeda', 'Token'])

      await classRadio(wrapper, 'FUNDS').trigger('change')
      await flushPromises()
      expect(typeLabels(wrapper)).toEqual(['Renda Fixa'])
    })

    it('offers the stock types under Ações and filters by several of them', async () => {
      const { wrapper, router, holdingsListStore } = await mountView({ query: '?filter=STOCKS' })

      expect(typeLabels(wrapper)).toEqual(['Ação PN', 'FII'])

      await typeCheckbox(wrapper, 'FII').setValue(true)
      await typeCheckbox(wrapper, 'Ação PN').setValue(true)
      await flushPromises()

      expect(holdingsListStore.loadKind).toHaveBeenLastCalledWith('STOCKS', 0, {
        typeLabel: ['FII', 'Ação PN'],
        walletId: undefined,
        search: undefined,
        sort: 'gainPct,desc',
      })
      expect(router.currentRoute.value.query).toEqual({
        filter: 'STOCKS',
        type: ['FII', 'Ação PN'],
      })
    })

    it('offers the fund types under Fundos', async () => {
      const { wrapper } = await mountView({ query: '?filter=FUNDS' })

      expect(typeLabels(wrapper)).toEqual(['Renda Fixa'])
    })
  })

  describe('chips and clearing', () => {
    it('shows one removable chip per active filter and counts them with the search', async () => {
      const { wrapper } = await mountView({
        query: '?filter=STOCKS&walletId=wallet-1&type=FII&hideFrozen=true&search=petr',
      })

      expect(chipLabels(wrapper)).toEqual(['Ações', 'Carteira B3', 'FII', 'Ocultar congelados'])
      expect(wrapper.get('[data-testid="active-filter-count"]').text()).toBe('5')
    })

    it('names an unknown wallet generically', async () => {
      const { wrapper } = await mountView({ query: '?walletId=wallet-gone' })

      expect(chipLabels(wrapper)).toEqual(['Carteira selecionada'])
    })

    it('removes the filter behind a chip', async () => {
      const { wrapper, router } = await mountView({
        query: '?filter=STOCKS&walletId=wallet-1&type=FII&hideFrozen=true',
      })

      for (const key of ['wallet-wallet-1', 'type-FII', 'hide-frozen', 'class-STOCKS']) {
        await wrapper.get(`[data-testid="chip-${key}"] .delete`).trigger('click')
        await flushPromises()
      }

      expect(chipLabels(wrapper)).toEqual([])
      expect(router.currentRoute.value.query).toEqual({})
    })

    it('labels each chip remover for screen readers', async () => {
      const { wrapper } = await mountView({ query: '?walletId=wallet-1' })

      expect(
        wrapper.get('[data-testid="chip-wallet-wallet-1"] .delete').attributes('aria-label'),
      ).toBe('Remover filtro Carteira B3')
    })

    it('clears every filter but the sort from the rail', async () => {
      const { wrapper, router, holdingsListStore } = await mountView({
        query:
          '?filter=STOCKS&walletId=wallet-1&type=FII&hideFrozen=true&search=petr&sort=gain,asc',
      })

      await wrapper.get('[data-testid="clear-filters"]').trigger('click')
      await flushPromises()

      expect(router.currentRoute.value.query).toEqual({ sort: 'gain,asc' })
      expect(holdingsListStore.loadKind).toHaveBeenCalledWith('all', 0, {
        typeLabel: undefined,
        walletId: undefined,
        search: undefined,
        sort: 'gain,asc',
      })
      expect(searchInput(wrapper).element.value).toBe('')
    })

    it('counts the results from the store', async () => {
      const { wrapper, holdingsListStore } = await mountView({ totalElements: 43 })

      expect(wrapper.get('[data-testid="result-count"]').text()).toBe('43 investimentos')
      holdingsListStore.totalElements = 1
      await flushPromises()
      expect(wrapper.get('[data-testid="result-count"]').text()).toBe('1 investimento')
    })
  })

  describe('frozen holdings filter', () => {
    it('requests the first page without frozen holdings once the box is checked', async () => {
      const { wrapper, router, holdingsListStore } = await mountView({
        query: '?filter=STOCKS&page=3',
      })

      await hideFrozenCheckbox(wrapper).setValue(true)
      await flushPromises()

      expect(holdingsListStore.loadKind).toHaveBeenCalledExactlyOnceWith('STOCKS', 0, {
        typeLabel: undefined,
        walletId: undefined,
        search: undefined,
        sort: 'gainPct,desc',
        hideFrozen: true,
      })
      expect(router.currentRoute.value.query).toEqual({ filter: 'STOCKS', hideFrozen: 'true' })
    })

    it('starts checked from the URL and shows frozen holdings again once unchecked', async () => {
      const { wrapper, router, holdingsListStore, initialLoad } = await mountView({
        query: '?hideFrozen=true',
      })
      const checkbox = hideFrozenCheckbox(wrapper)

      expect(checkbox.element.checked).toBe(true)
      expect(initialLoad).toEqual([['all', 0, expect.objectContaining({ hideFrozen: true })]])

      await checkbox.setValue(false)
      await flushPromises()

      expect(holdingsListStore.loadKind).toHaveBeenCalledTimes(1)
      expect(vi.mocked(holdingsListStore.loadKind).mock.lastCall?.[2]?.hideFrozen).toBeUndefined()
      expect(router.currentRoute.value.query).toEqual({})
    })

    it('carries the box into the exported report', async () => {
      const openWindow = vi.spyOn(window, 'open').mockReturnValue(null)
      const { wrapper } = await mountView({ query: '?hideFrozen=true' })

      await exportButton(wrapper).trigger('click')

      expect(String(openWindow.mock.calls[0][0])).toBe('/investments/report?hideFrozen=true')
    })
  })

  describe('search', () => {
    it('waits for the typing to pause, then reloads the first page with the trimmed term', async () => {
      const { wrapper, router, holdingsListStore } = await mountView({ query: '?page=2' })
      vi.useFakeTimers()

      await searchInput(wrapper).setValue('  petr  ')
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
        sort: 'gainPct,desc',
      })
      expect(router.currentRoute.value.query).toEqual({ search: 'petr' })
    })

    it('restarts the pause when the term keeps changing', async () => {
      const { wrapper, holdingsListStore } = await mountView()
      vi.useFakeTimers()

      await searchInput(wrapper).setValue('pe')
      vi.advanceTimersByTime(200)
      await searchInput(wrapper).setValue('petr')
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

      await searchInput(wrapper).setValue('   ')
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

      await headerCell(wrapper, 'Preço atual').trigger('click')
      await headerCell(wrapper, 'Preço atual').trigger('click')
      await flushPromises()

      expect(holdingsListStore.loadKind).toHaveBeenLastCalledWith(
        'all',
        0,
        expect.objectContaining({ sort: 'price,desc' }),
      )
    })

    it('starts ascending again on a different column', async () => {
      const { wrapper, holdingsListStore } = await mountView({ query: '?sort=gain,desc' })

      await headerCell(wrapper, 'Carteira · tipo').trigger('click')
      await flushPromises()

      expect(holdingsListStore.loadKind).toHaveBeenLastCalledWith(
        'all',
        0,
        expect.objectContaining({ sort: 'wallet,asc' }),
      )
    })

    it('opens sorted by gain percentage descending, marked on the Resultado header', async () => {
      const { wrapper, router, initialLoad } = await mountView()

      expect(initialLoad[0][2]).toEqual(expect.objectContaining({ sort: 'gainPct,desc' }))
      const resultHeader = headerCell(wrapper, 'Resultado')
      expect(resultHeader.find('.sort-icon-active').exists()).toBe(true)
      expect(resultHeader.find('.mdi-chevron-down').exists()).toBe(true)
      expect(router.currentRoute.value.query).toEqual({})
    })

    it('flips the default Resultado sort to ascending on its first click', async () => {
      const { wrapper, router, holdingsListStore } = await mountView()

      await headerCell(wrapper, 'Resultado').trigger('click')
      await flushPromises()

      expect(holdingsListStore.loadKind).toHaveBeenLastCalledWith(
        'all',
        0,
        expect.objectContaining({ sort: 'gainPct,asc' }),
      )
      expect(router.currentRoute.value.query).toEqual({ sort: 'gainPct,asc' })
    })

    it('sorts by asset from A to Z, then Z to A, on the Ativo header', async () => {
      const { wrapper, holdingsListStore } = await mountView()

      await headerCell(wrapper, 'Ativo').trigger('click')
      await flushPromises()
      expect(holdingsListStore.loadKind).toHaveBeenLastCalledWith(
        'all',
        0,
        expect.objectContaining({ sort: 'asset,asc' }),
      )

      await headerCell(wrapper, 'Ativo').trigger('click')
      await flushPromises()
      expect(holdingsListStore.loadKind).toHaveBeenLastCalledWith(
        'all',
        0,
        expect.objectContaining({ sort: 'asset,desc' }),
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

    it('closes the drawer when the page changes', async () => {
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
      expect(drawerOpen()).toBe(true)

      await wrapper.find('.pagination-next').trigger('click')
      await flushPromises()

      expect(drawerOpen()).toBe(false)
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
        walletId: ['wallet-3'],
        search: undefined,
        sort: 'gainPct,desc',
      })
      expect(activeClass(wrapper)).toBe('FUNDS')
    })

    it('closes the drawer on an external navigation', async () => {
      vi.mocked(holdingsApi.getStockHolding).mockResolvedValue(stockDetail)
      const { wrapper, router } = await mountView({ rows: [stockRow] })
      await bodyRows(wrapper)[0].trigger('click')
      await flushPromises()
      expect(drawerOpen()).toBe(true)

      await router.push('/investments?filter=STOCKS')
      await flushPromises()

      expect(drawerOpen()).toBe(false)
    })

    it('does not reload a second time for the URL it wrote itself', async () => {
      const { wrapper, holdingsListStore } = await mountView()

      await classRadio(wrapper, 'STOCKS').trigger('change')
      await flushPromises()
      await headerCell(wrapper, 'Valor atual').trigger('click')
      await flushPromises()

      expect(holdingsListStore.loadKind).toHaveBeenCalledTimes(2)
    })
  })

  describe('holding drawer', () => {
    const fundDetail = {
      id: 'holding-3',
      walletId: 'wallet-3',
      typeId: 'type-3',
      typeName: 'Renda Fixa',
      segmentId: null,
      segmentName: null,
      name: 'Tesouro IPCA+',
      currentValue: 3300,
      administrationFeeRate: null,
      performanceFeeRate: null,
      contributions: [],
      frozen: false,
      withdrawals: [],
    }

    it('opens a row in the drawer and fetches that holding', async () => {
      vi.mocked(holdingsApi.getStockHolding).mockResolvedValue(stockDetail)
      const { wrapper } = await mountView()

      await bodyRows(wrapper)[0].trigger('click')
      await flushPromises()

      expect(drawerOpen()).toBe(true)
      expect(bodyRows(wrapper)[0].classes()).toContain('is-current')
      expect(holdingsApi.getStockHolding).toHaveBeenCalledTimes(1)
      expect(holdingsApi.getStockHolding).toHaveBeenCalledWith('wallet-1', 'holding-1')
      expect(drawerText()).toContain('Movimentações')
    })

    it('shows the figures in the display currency, as the table does', async () => {
      vi.mocked(holdingsApi.getStockHolding).mockResolvedValue(stockDetail)
      const { wrapper } = await mountView({ rows: [stockRow] })

      await bodyRows(wrapper)[0].trigger('click')
      await flushPromises()

      expect(drawerText()).toContain('R$ 6.960,00')
    })

    it('fetches a crypto and a fund holding through their own endpoints', async () => {
      vi.mocked(holdingsApi.getCryptoHolding).mockResolvedValue({
        id: 'holding-2',
        walletId: 'wallet-2',
        typeId: null,
        typeName: null,
        segmentId: null,
        segmentName: null,
        ticker: 'BTC',
        name: 'Bitcoin',
        currentPrice: 180000,
        lots: [],
        frozen: false,
        withdrawals: [],
      })
      vi.mocked(holdingsApi.getFundHolding).mockResolvedValue(fundDetail)
      const { wrapper } = await mountView()

      await bodyRows(wrapper)[1].trigger('click')
      await flushPromises()
      expect(holdingsApi.getCryptoHolding).toHaveBeenCalledWith('wallet-2', 'holding-2')

      await bodyRows(wrapper)[2].trigger('click')
      await flushPromises()
      expect(holdingsApi.getFundHolding).toHaveBeenCalledWith('wallet-3', 'holding-3')
    })

    it('keeps a single drawer open at a time', async () => {
      vi.mocked(holdingsApi.getStockHolding).mockResolvedValue(stockDetail)
      vi.mocked(holdingsApi.getFundHolding).mockResolvedValue(fundDetail)
      const { wrapper } = await mountView()

      await bodyRows(wrapper)[0].trigger('click')
      await flushPromises()
      await bodyRows(wrapper)[2].trigger('click')
      await flushPromises()

      expect(document.body.querySelectorAll('[data-testid="holding-drawer"]')).toHaveLength(1)
      expect(bodyRows(wrapper)[0].classes()).not.toContain('is-current')
      expect(bodyRows(wrapper)[2].classes()).toContain('is-current')
      expect(drawerText()).toContain('Tesouro IPCA+')
    })

    it('closes the drawer from its close button', async () => {
      vi.mocked(holdingsApi.getStockHolding).mockResolvedValue(stockDetail)
      const { wrapper } = await mountView()
      await bodyRows(wrapper)[0].trigger('click')
      await flushPromises()

      document.body
        .querySelector<HTMLElement>('[data-drawer-close]')!
        .dispatchEvent(new MouseEvent('click', { bubbles: true }))
      await flushPromises()

      expect(drawerOpen()).toBe(false)
      expect(bodyRows(wrapper)[0].classes()).not.toContain('is-current')
    })

    it('closes the drawer when the kind tab changes', async () => {
      vi.mocked(holdingsApi.getStockHolding).mockResolvedValue(stockDetail)
      const { wrapper } = await mountView()
      await bodyRows(wrapper)[0].trigger('click')
      await flushPromises()
      expect(drawerOpen()).toBe(true)

      await classRadio(wrapper, 'STOCKS').trigger('change')
      await flushPromises()

      expect(drawerOpen()).toBe(false)
    })

    it('closes the drawer when a wallet or type filter changes', async () => {
      vi.mocked(holdingsApi.getStockHolding).mockResolvedValue(stockDetail)
      const { wrapper } = await mountView({ query: '?filter=STOCKS' })
      await bodyRows(wrapper)[0].trigger('click')
      await flushPromises()
      expect(drawerOpen()).toBe(true)

      await typeCheckbox(wrapper, 'FII').setValue(true)
      await flushPromises()
      expect(drawerOpen()).toBe(false)

      await bodyRows(wrapper)[0].trigger('click')
      await flushPromises()
      expect(drawerOpen()).toBe(true)
      await walletCheckbox(wrapper, 'wallet-1').setValue(true)
      await flushPromises()
      expect(drawerOpen()).toBe(false)
    })
  })

  describe('reacting to the drawer', () => {
    async function openStockDrawer() {
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

    it('closes the drawer and refreshes the list after the holding is removed', async () => {
      vi.mocked(holdingsApi.deleteStockHolding).mockResolvedValue(undefined)
      const { holdingsListStore } = await openStockDrawer()

      document.body
        .querySelector<HTMLElement>('[data-testid="holding-remove"]')!
        .dispatchEvent(new MouseEvent('click', { bubbles: true }))
      await flushPromises()
      dialogButton('Remover').click()
      await flushPromises()

      expect(holdingsApi.deleteStockHolding).toHaveBeenCalledWith('wallet-1', 'holding-1')
      expect(holdingsListStore.refresh).toHaveBeenCalledTimes(1)
      expect(drawerOpen()).toBe(false)
    })

    it('freezes the holding from the drawer and shows the new state once the list reloads', async () => {
      vi.mocked(holdingsApi.updateStockHolding).mockResolvedValue({ ...stockDetail, frozen: true })
      const { wrapper, holdingsListStore } = await openStockDrawer()
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
      expect(drawerOpen()).toBe(true)
      expect(bodyRows(wrapper)[0].find('[data-testid="frozen-tag"]').exists()).toBe(true)
      expect(
        document.body.querySelector('[data-testid="holding-drawer"] [data-testid="frozen-tag"]'),
      ).not.toBeNull()
      expect(document.body.querySelector('[data-testid="holding-freeze"]')!.textContent).toContain(
        'Descongelar',
      )
    })

    it('refreshes the list but keeps the drawer open after a position changes', async () => {
      vi.mocked(holdingsApi.deleteStockLot).mockResolvedValue(undefined)
      const { holdingsListStore } = await openStockDrawer()

      document.body
        .querySelector<HTMLElement>('[data-testid="ledger-delete"]')!
        .dispatchEvent(new MouseEvent('click', { bubbles: true }))
      await flushPromises()
      dialogButton('Remover').click()
      await flushPromises()

      expect(holdingsApi.deleteStockLot).toHaveBeenCalledWith('wallet-1', 'holding-1', 'lot-1')
      expect(holdingsListStore.refresh).toHaveBeenCalledTimes(1)
      expect(drawerOpen()).toBe(true)
    })

    it('opens the purchase modal from the row without opening the drawer', async () => {
      const { wrapper, holdingsListStore } = await mountView({ rows: [stockRow] })

      await bodyRows(wrapper)[0].get('[data-testid="row-add-position"]').trigger('click')
      await flushPromises()

      expect(drawerOpen()).toBe(false)
      expect(document.body.querySelector('.modal-card-title')?.textContent).toBe('Registrar compra')
      expect(holdingsListStore.refresh).not.toHaveBeenCalled()
    })
  })

  describe('toolbar', () => {
    it('opens the add-investment modal without a kind on the Todos tab', async () => {
      const { wrapper, modals } = await mountView()

      await headerButton(wrapper, 'Adicionar investimento').trigger('click')

      expect(modals.openAddInvestment).toHaveBeenCalledWith(undefined, undefined)
    })

    it('opens the add-investment modal pre-set to the active kind', async () => {
      const { wrapper, modals } = await mountView({ query: '?filter=FUNDS' })

      await headerButton(wrapper, 'Adicionar investimento').trigger('click')

      expect(modals.openAddInvestment).toHaveBeenCalledWith('FUNDS', undefined)
    })

    it('opens the add-investment modal on the filtered kind and wallet', async () => {
      const { wrapper, modals } = await mountView({ query: '?filter=STOCKS&walletId=wallet-1' })

      await headerButton(wrapper, 'Adicionar investimento').trigger('click')

      expect(modals.openAddInvestment).toHaveBeenCalledWith('STOCKS', 'wallet-1')
    })

    it("opens the add-investment modal on the filtered wallet's kind when no kind is filtered", async () => {
      const { wrapper, modals } = await mountView({ query: '?walletId=wallet-2' })

      await headerButton(wrapper, 'Adicionar investimento').trigger('click')

      expect(modals.openAddInvestment).toHaveBeenCalledWith('CRYPTO', 'wallet-2')
    })

    it('preselects no wallet when several are filtered', async () => {
      const { wrapper, modals } = await mountView({ query: '?walletId=wallet-1&walletId=wallet-2' })

      await headerButton(wrapper, 'Adicionar investimento').trigger('click')

      expect(modals.openAddInvestment).toHaveBeenCalledWith(undefined, undefined)
    })

    it('carries several wallets and types into the report as repeated keys', async () => {
      const openWindow = vi.spyOn(window, 'open').mockReturnValue(null)
      const { wrapper } = await mountView({
        query: '?filter=STOCKS&walletId=wallet-1&walletId=wallet-4&type=FII&type=ETF',
      })

      await exportButton(wrapper).trigger('click')

      expect(String(openWindow.mock.calls[0][0])).toBe(
        '/investments/report?filter=STOCKS&walletId=wallet-1&walletId=wallet-4&type=FII&type=ETF',
      )
    })

    it('opens the report in a new tab carrying the active filters but not the sort or page', async () => {
      const openWindow = vi.spyOn(window, 'open').mockReturnValue(null)
      const { wrapper } = await mountView({
        query: '?filter=STOCKS&walletId=wallet-1&type=FII&search=petr&sort=gain,asc&page=2',
      })

      await exportButton(wrapper).trigger('click')

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

      await exportButton(wrapper).trigger('click')

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
