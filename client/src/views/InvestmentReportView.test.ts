import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createMemoryHistory, createRouter } from 'vue-router'
import InvestmentReportView from './InvestmentReportView.vue'
import { holdingsApi } from '@/api/holdings'
import { useCurrencyStore } from '@/stores/currency'
import { useRatesStore } from '@/stores/rates'
import { useWalletsStore } from '@/stores/wallets'
import type { HoldingRow, WalletResponse } from '@/types'

vi.mock('@/api/holdings', () => ({ holdingsApi: { findAllForReport: vi.fn() } }))
vi.mock('@/api/profile', () => ({ profileApi: {} }))
vi.mock('@/api/rates', () => ({ ratesApi: {} }))
vi.mock('@/api/wallets', () => ({ walletsApi: {} }))

function holdingOf(overrides: Partial<HoldingRow>): HoldingRow {
  return {
    id: 'holding-1',
    kind: 'STOCKS',
    name: 'Petrobras',
    ticker: 'PETR4',
    typeLabel: 'Ordinária',
    walletId: 'wallet-1',
    walletName: 'Ações BR',
    walletCurrency: 'BRL',
    quantity: 10,
    costBasis: 300,
    currentPrice: 40,
    currentValue: 400,
    gain: 100,
    gainPct: 33.33,
    ...overrides,
  }
}

const holdings: HoldingRow[] = [
  holdingOf({
    id: 'holding-2',
    name: 'Vale',
    ticker: 'VALE3',
    quantity: 5,
    costBasis: 400,
    currentPrice: 70,
    currentValue: 350,
  }),
  holdingOf({}),
  holdingOf({
    id: 'holding-3',
    name: 'Itaú',
    ticker: 'ITUB4',
    typeLabel: 'Preferencial',
    walletId: 'wallet-2',
    walletName: 'Corretora B',
    quantity: 2.5,
    costBasis: 100,
    currentPrice: 50,
    currentValue: 125,
  }),
  holdingOf({
    id: 'holding-4',
    kind: 'CRYPTO',
    name: 'Bitcoin',
    ticker: 'BTC',
    typeLabel: null,
    walletId: 'wallet-3',
    walletName: 'Cripto',
    quantity: 0.5,
    costBasis: 1000,
    currentPrice: 3000,
    currentValue: 1500,
  }),
  holdingOf({
    id: 'holding-5',
    kind: 'FUNDS',
    name: 'Tesouro Selic',
    ticker: null,
    typeLabel: null,
    walletId: 'wallet-4',
    walletName: 'Fundos',
    quantity: null,
    costBasis: 500,
    currentPrice: null,
    currentValue: 520,
  }),
]

let activeWrapper: VueWrapper | undefined
let observerCallbacks: (() => void)[] = []
let disconnectSpy: () => void

class ResizeObserverStub {
  constructor(callback: () => void) {
    observerCallbacks.push(callback)
  }
  observe() {}
  disconnect() {
    disconnectSpy()
  }
}

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

async function mountView(
  options: {
    query?: string
    rows?: HoldingRow[]
    displayCurrency?: string
    convertFactor?: number
    knownWallet?: WalletResponse
  } = {},
) {
  const {
    query = '',
    rows = holdings,
    displayCurrency = 'BRL',
    convertFactor = 1,
    knownWallet,
  } = options
  vi.mocked(holdingsApi.findAllForReport).mockResolvedValue(rows)

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/investments/report', name: 'investment-report', component: InvestmentReportView },
      { path: '/investments', name: 'investments', component: { template: '<div />' } },
    ],
  })
  router.push(`/investments/report${query}`)
  await router.isReady()

  const pinia = createTestingPinia()
  const currencyStore = useCurrencyStore()
  currencyStore.displayCurrency = displayCurrency
  vi.mocked(currencyStore.convert).mockImplementation((amount) => amount * convertFactor)

  vi.mocked(useWalletsStore().walletById).mockReturnValue(knownWallet)

  const wrapper = mount(InvestmentReportView, {
    global: { plugins: [pinia, router] },
    attachTo: document.body,
  })
  activeWrapper = wrapper
  await flushPromises()
  return { wrapper, router, currencyStore }
}

function kindSection(wrapper: VueWrapper, label: string) {
  return wrapper
    .findAll('.report-kind-section')
    .find((section) => section.find('.report-kind-head h2').text() === label)!
}

function subtotals(container: Pick<VueWrapper, 'find'>, selector: string) {
  return container
    .find(selector)
    .findAll('.stotal-value')
    .map((value) => value.text())
}

describe('InvestmentReportView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    observerCallbacks = []
    disconnectSpy = vi.fn()
    vi.stubGlobal('ResizeObserver', ResizeObserverStub)
  })

  afterEach(() => {
    activeWrapper?.unmount()
    activeWrapper = undefined
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('loads the currency, rates and wallets stores and requests an unfiltered report', async () => {
    await mountView()

    expect(useCurrencyStore().load).toHaveBeenCalledTimes(1)
    expect(useRatesStore().load).toHaveBeenCalledTimes(1)
    expect(useWalletsStore().load).toHaveBeenCalledTimes(1)
    expect(holdingsApi.findAllForReport).toHaveBeenCalledWith({
      kind: undefined,
      typeLabel: undefined,
      walletId: undefined,
      search: undefined,
    })
  })

  it('forwards the investments list filters from the query string to the report request', async () => {
    await mountView({ query: '?filter=CRYPTO&type=Ordin%C3%A1ria&walletId=wallet-1&search=btc' })

    expect(holdingsApi.findAllForReport).toHaveBeenCalledWith({
      kind: 'CRYPTO',
      typeLabel: 'Ordinária',
      walletId: 'wallet-1',
      search: 'btc',
    })
  })

  it('ignores a kind filter that is not a valid wallet kind', async () => {
    const { wrapper } = await mountView({ query: '?filter=BONDS' })

    expect(holdingsApi.findAllForReport).toHaveBeenCalledWith(
      expect.objectContaining({ kind: undefined }),
    )
    expect(wrapper.find('.report-meta').text()).not.toContain('Filtros')
  })

  it('lists the active filters in the report header', async () => {
    const { wrapper } = await mountView({
      query: '?filter=STOCKS&type=Preferencial&walletId=wallet-9&search=itau',
    })

    expect(wrapper.find('.report-meta').text()).toContain(
      'Filtros: Ações, Preferencial, Carteira selecionada, "itau"',
    )
  })

  it('names the filtered wallet when the wallets store knows it', async () => {
    const { wrapper } = await mountView({
      query: '?walletId=wallet-1',
      knownWallet: { id: 'wallet-1', name: 'Ações BR' } as WalletResponse,
    })

    expect(wrapper.find('.report-meta').text()).toContain('Filtros: Ações BR')
  })

  it('shows the empty state instead of a report when there are no holdings', async () => {
    const { wrapper } = await mountView({ rows: [] })

    expect(wrapper.find('.empty-title').text()).toBe('Nenhum investimento para este relatório')
    expect(wrapper.find('.report-paper').exists()).toBe(false)
  })

  it('nests the report kind, then type or ticker, then wallet, in a fixed order', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.findAll('.report-kind-head h2').map((heading) => heading.text())).toEqual([
      'Ações',
      'Cripto',
      'Fundos',
    ])

    const stocks = kindSection(wrapper, 'Ações')
    expect(stocks.findAll('.report-subgroup-title').map((title) => title.text())).toEqual([
      'Ordinária',
      'Preferencial',
    ])
    expect(stocks.findAll('.report-wallet-name').map((name) => name.text())).toEqual([
      'Ações BR',
      'Corretora B',
    ])
    expect(
      stocks
        .findAll('.report-subgroup')[0]
        .findAll('tbody tr:not(.report-subtotal-row) td:first-child')
        .map((cell) => cell.text()),
    ).toEqual(['PETR4', 'VALE3'])

    expect(
      kindSection(wrapper, 'Cripto')
        .findAll('.report-subgroup-title')
        .map((title) => title.text()),
    ).toEqual(['BTC'])
    expect(
      kindSection(wrapper, 'Fundos')
        .findAll('.report-subgroup-title')
        .map((title) => title.text()),
    ).toEqual(['Outros'])
  })

  it('shows a subtotal at the wallet, sub-group, kind and grand-total levels', async () => {
    const { wrapper } = await mountView()

    const grandTotal = wrapper.find('.report-grand-total').text()
    expect(grandTotal).toContain('R$ 2.300,00')
    expect(grandTotal).toContain('R$ 2.895,00')
    expect(grandTotal).toContain('+R$ 595,00')

    const stocks = kindSection(wrapper, 'Ações')
    expect(subtotals(stocks, '.report-kind-head')).toEqual(['R$ 800,00', 'R$ 875,00'])

    const ordinary = stocks.findAll('.report-subgroup')[0]
    expect(subtotals(ordinary, '.report-subgroup-head')).toEqual(['R$ 700,00', 'R$ 750,00'])

    const walletSubtotal = ordinary.find('.report-subtotal-row').text()
    expect(walletSubtotal).toContain('R$ 700,00')
    expect(walletSubtotal).toContain('R$ 750,00')
    expect(walletSubtotal).toContain('+R$ 50,00')
  })

  it('renders each holding row with quantity, price, cost, value and result', async () => {
    const { wrapper } = await mountView()

    const itau = kindSection(wrapper, 'Ações').findAll('.report-subgroup')[1]
    const cells = itau
      .findAll('tbody tr')[0]
      .findAll('td')
      .map((cell) => cell.text())

    expect(cells[0]).toBe('ITUB4')
    expect(cells[1]).toBe('2,5')
    expect(cells[2]).toBe('R$ 50,00')
    expect(cells[3]).toBe('R$ 100,00')
    expect(cells[4]).toBe('R$ 125,00')
    expect(cells[5]).toContain('+R$ 25,00')
  })

  it('falls back to the name and dashes for a holding without ticker, quantity or price', async () => {
    const { wrapper } = await mountView()

    const cells = kindSection(wrapper, 'Fundos')
      .findAll('tbody tr')[0]
      .findAll('td')
      .map((cell) => cell.text())

    expect(cells[0]).toBe('Tesouro Selic')
    expect(cells[1]).toBe('—')
    expect(cells[2]).toBe('—')
    expect(cells[3]).toBe('R$ 500,00')
  })

  it('converts every figure into the display currency', async () => {
    const { wrapper } = await mountView({ displayCurrency: 'USD', convertFactor: 0.5 })

    const grandTotal = wrapper.find('.report-grand-total').text()

    expect(grandTotal).toContain('US$ 1.150,00')
    expect(grandTotal).toContain('US$ 1.447,50')
    const petrobras = kindSection(wrapper, 'Ações').findAll('tbody tr')[0].findAll('td')
    expect(petrobras[2].text()).toBe('US$ 20,00')
  })

  it('prints the page when Imprimir is clicked', async () => {
    const printSpy = vi.spyOn(window, 'print').mockImplementation(() => undefined)
    const { wrapper } = await mountView()

    await wrapper.find('.report-toolbar button').trigger('click')

    expect(printSpy).toHaveBeenCalledTimes(1)
  })

  it('links back to the investments list', async () => {
    const { wrapper, router } = await mountView()

    await wrapper.find('.back-link').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('investments')
  })

  it('draws a guide line per extra printed page once the paper is measured', async () => {
    vi.spyOn(HTMLElement.prototype, 'scrollHeight', 'get').mockReturnValue(2500)
    const { wrapper } = await mountView()

    expect(wrapper.findAll('.report-page-break-label').map((label) => label.text())).toEqual([
      'Página 2',
      'Página 3',
    ])
  })

  it('draws no guide line when the report fits one page', async () => {
    vi.spyOn(HTMLElement.prototype, 'scrollHeight', 'get').mockReturnValue(600)
    const { wrapper } = await mountView()

    expect(wrapper.find('.report-page-break').exists()).toBe(false)
  })

  it('recomputes the guide lines when the paper is resized', async () => {
    const heightSpy = vi.spyOn(HTMLElement.prototype, 'scrollHeight', 'get').mockReturnValue(600)
    const { wrapper } = await mountView()

    heightSpy.mockReturnValue(2500)
    observerCallbacks.forEach((callback) => callback())
    await flushPromises()

    expect(wrapper.findAll('.report-page-break')).toHaveLength(2)
  })

  it('stops observing the paper when the view is unmounted', async () => {
    const { wrapper } = await mountView()

    wrapper.unmount()
    activeWrapper = undefined

    expect(disconnectSpy).toHaveBeenCalled()
  })
})
