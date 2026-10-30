import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import HoldingDrawer from './HoldingDrawer.vue'
import { holdingsApi } from '@/api/holdings'
import { resultsApi } from '@/api/results'
import { useAuthStore } from '@/stores/auth'
import { useTypesListStore } from '@/stores/typesList'
import { useCurrencyStore } from '@/stores/currency'
import { useReinvestmentsStore } from '@/stores/reinvestments'
import { useWalletsStore } from '@/stores/wallets'
import type {
  ContributionDetail,
  CryptoHoldingDetail,
  LotDetail,
  FundHoldingDetail,
  HoldingDetail,
  HoldingRow,
  StockHoldingDetail,
  WalletResponse,
  WithdrawalDetail,
} from '@/types'

vi.mock('@/api/holdings', () => ({
  holdingsApi: {
    findAll: vi.fn(),
    getStockHolding: vi.fn(),
    getCryptoHolding: vi.fn(),
    getFundHolding: vi.fn(),
    addStockLot: vi.fn(),
    addCryptoLot: vi.fn(),
    addContribution: vi.fn(),
    updateStockHolding: vi.fn(),
    updateCryptoHolding: vi.fn(),
    updateFundHolding: vi.fn(),
    updateHoldingSegment: vi.fn(),
    deleteStockHolding: vi.fn(),
    deleteCryptoHolding: vi.fn(),
    deleteFundHolding: vi.fn(),
    deleteStockLot: vi.fn(),
    deleteCryptoLot: vi.fn(),
    deleteFundContribution: vi.fn(),
    updateStockLot: vi.fn(),
    updateCryptoLot: vi.fn(),
    updateFundContribution: vi.fn(),
  },
}))

vi.mock('@/api/results', () => ({
  resultsApi: {
    withdrawFromStockHolding: vi.fn(),
    withdrawFromCryptoHolding: vi.fn(),
    withdrawFromFundHolding: vi.fn(),
    deleteStockWithdrawal: vi.fn(),
    deleteCryptoWithdrawal: vi.fn(),
    deleteFundWithdrawal: vi.fn(),
  },
}))

vi.mock('@/api/reinvestments', () => ({
  reinvestmentsApi: { findAll: vi.fn(), reinvest: vi.fn() },
}))
vi.mock('@/api/walletMoves', () => ({ walletMovesApi: { findAll: vi.fn(), move: vi.fn() } }))
vi.mock('@/api/wallets', () => ({ walletsApi: { findAll: vi.fn() } }))

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
  id: 'holding-3',
  kind: 'CRYPTO',
  name: 'Bitcoin',
  ticker: 'BTC',
  typeLabel: null,
  segmentLabel: null,
  walletId: 'wallet-3',
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
  id: 'holding-2',
  kind: 'FUNDS',
  name: 'Tesouro IPCA+',
  ticker: null,
  typeLabel: 'Renda Fixa',
  segmentLabel: null,
  walletId: 'wallet-2',
  walletName: 'Carteira Fundos',
  walletCurrency: 'BRL',
  quantity: null,
  costBasis: 3000,
  currentPrice: null,
  currentValue: 3000,
  gain: 0,
  gainPct: 0,
  frozen: false,
}

function withdrawalOf(overrides: Partial<WithdrawalDetail> = {}): WithdrawalDetail {
  return {
    id: 'w-1',
    resultType: 'WITHDRAWAL',
    resultDate: '2026-05-10',
    quantity: 150,
    grossAmount: 4575,
    fees: 10,
    taxes: 120,
    costBasis: 4239,
    netAmount: 4445,
    profit: 206,
    ...overrides,
  }
}

function stockDetailWithWithdrawals(): StockHoldingDetail {
  return {
    id: 'holding-1',
    walletId: 'wallet-1',
    typeId: 'type-1',
    typeName: 'Ação PN',
    segmentId: null,
    segmentName: null,
    ticker: 'PETR4',
    name: 'Petróleo Brasileiro',
    currentPrice: 34.8,
    lots: [
      { id: 'lot-1', lotDate: '2026-01-12', quantity: 300, price: 25.1 },
      { id: 'lot-2', lotDate: '2026-03-03', quantity: 200, price: 33 },
    ],
    frozen: false,
    withdrawals: [
      withdrawalOf(),
      withdrawalOf({
        id: 'w-2',
        resultDate: '2026-06-18',
        grossAmount: 4815,
        taxes: 126,
        netAmount: 4679,
        profit: 440,
      }),
    ],
  }
}

function stockDetailWithoutWithdrawals(): StockHoldingDetail {
  return {
    id: 'holding-1',
    walletId: 'wallet-1',
    typeId: 'type-1',
    typeName: 'Ação PN',
    segmentId: null,
    segmentName: null,
    ticker: 'PETR4',
    name: 'Petróleo Brasileiro',
    currentPrice: 34.8,
    lots: [{ id: 'lot-1', lotDate: '2026-01-12', quantity: 300, price: 25.1 }],
    frozen: false,
    withdrawals: [],
  }
}

function cryptoDetail(): CryptoHoldingDetail {
  return {
    id: 'holding-3',
    walletId: 'wallet-3',
    typeId: null,
    typeName: null,
    segmentId: null,
    segmentName: null,
    ticker: 'BTC',
    name: 'Bitcoin',
    currentPrice: 180000,
    lots: [{ id: 'lot-9', lotDate: '2026-02-01', quantity: 0.5, price: 200000 }],
    frozen: false,
    withdrawals: [],
  }
}

function fundDetail(overrides: Partial<FundHoldingDetail> = {}): FundHoldingDetail {
  return {
    id: 'holding-2',
    walletId: 'wallet-2',
    typeId: 'type-2',
    typeName: 'Renda Fixa',
    segmentId: null,
    segmentName: null,
    name: 'Tesouro IPCA+',
    currentValue: 3000,
    administrationFeeRate: null,
    performanceFeeRate: null,
    contributions: [{ id: 'c-1', contributionDate: '2026-01-10', amount: 4000 }],
    frozen: false,
    withdrawals: [
      withdrawalOf({
        id: 'fw-1',
        resultDate: '2026-05-10',
        quantity: null,
        grossAmount: 1000,
        fees: 5,
        taxes: 10,
        costBasis: 1000,
        netAmount: 985,
        profit: -15,
      }),
    ],
    ...overrides,
  }
}

const newLot: LotDetail = { id: 'lot-new', lotDate: '2026-03-15', quantity: 10, price: 30 }

const newContribution: ContributionDetail = {
  id: 'contribution-new',
  contributionDate: '2026-03-15',
  amount: 500,
}

type ApiMock = ReturnType<typeof vi.fn>

interface KindCase {
  label: string
  row: HoldingRow
  detail: () => HoldingDetail
  getHolding: ApiMock
  updateHolding: ApiMock
  deleteHolding: ApiMock
  deleteEntry: ApiMock
  deleteWithdrawal: ApiMock
  updateEntry: ApiMock
  entryId: string
  dateField: 'lotDate' | 'contributionDate'
}

const kindCases: KindCase[] = [
  {
    label: 'stock',
    row: stockRow,
    detail: stockDetailWithoutWithdrawals,
    getHolding: vi.mocked(holdingsApi.getStockHolding),
    updateHolding: vi.mocked(holdingsApi.updateStockHolding),
    deleteHolding: vi.mocked(holdingsApi.deleteStockHolding),
    deleteEntry: vi.mocked(holdingsApi.deleteStockLot),
    deleteWithdrawal: vi.mocked(resultsApi.deleteStockWithdrawal),
    updateEntry: vi.mocked(holdingsApi.updateStockLot),
    entryId: 'lot-1',
    dateField: 'lotDate',
  },
  {
    label: 'crypto',
    row: cryptoRow,
    detail: cryptoDetail,
    getHolding: vi.mocked(holdingsApi.getCryptoHolding),
    updateHolding: vi.mocked(holdingsApi.updateCryptoHolding),
    deleteHolding: vi.mocked(holdingsApi.deleteCryptoHolding),
    deleteEntry: vi.mocked(holdingsApi.deleteCryptoLot),
    deleteWithdrawal: vi.mocked(resultsApi.deleteCryptoWithdrawal),
    updateEntry: vi.mocked(holdingsApi.updateCryptoLot),
    entryId: 'lot-9',
    dateField: 'lotDate',
  },
  {
    label: 'fund',
    row: fundRow,
    detail: fundDetail,
    getHolding: vi.mocked(holdingsApi.getFundHolding),
    updateHolding: vi.mocked(holdingsApi.updateFundHolding),
    deleteHolding: vi.mocked(holdingsApi.deleteFundHolding),
    deleteEntry: vi.mocked(holdingsApi.deleteFundContribution),
    deleteWithdrawal: vi.mocked(resultsApi.deleteFundWithdrawal),
    updateEntry: vi.mocked(holdingsApi.updateFundContribution),
    entryId: 'c-1',
    dateField: 'contributionDate',
  },
]

let activeWrapper: VueWrapper | undefined

interface DrawerOptions {
  isAdmin?: boolean
  convertToDisplayCurrency?: boolean
  conversionRate?: number
}

function mountDrawer(row: HoldingRow, options: DrawerOptions = {}) {
  const pinia = createTestingPinia()
  const currencyStore = useCurrencyStore()
  vi.mocked(currencyStore.convert).mockImplementation(
    (amount: number) => amount * (options.conversionRate ?? 1),
  )
  currencyStore.displayCurrency = 'BRL'
  const wrapper = mount(HoldingDrawer, {
    props: { row, convertToDisplayCurrency: options.convertToDisplayCurrency ?? true },
    global: { plugins: [pinia], config: { errorHandler: () => undefined } },
    attachTo: document.body,
  })
  activeWrapper = wrapper
  useAuthStore().session = {
    name: 'Admin',
    email: 'admin@admin.com',
    role: options.isAdmin === false ? 'USER' : 'ADMIN',
    status: 'APPROVED',
    authProvider: 'LOCAL',
    demoModeEnabled: false,
  }
  return wrapper
}

async function mountLoadedDrawer(
  row: HoldingRow,
  detail: HoldingDetail,
  options: DrawerOptions = {},
) {
  for (const kindCase of kindCases) {
    if (kindCase.row.kind === row.kind) kindCase.getHolding.mockResolvedValue(detail)
  }
  const wrapper = mountDrawer(row, options)
  await flushPromises()
  return wrapper
}

function ledgerItems(wrapper: VueWrapper) {
  return wrapper.findAll('.ledger-item')
}

function deleteButton(item: ReturnType<typeof ledgerItems>[number]) {
  return item.find('[data-testid="ledger-delete"]')
}

function bodyButton(label: string) {
  return Array.from(document.body.querySelectorAll('button')).find(
    (button) => button.textContent?.trim() === label,
  ) as HTMLButtonElement | undefined
}

async function confirmDialog(label: string) {
  await flushPromises()
  bodyButton(label)?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
  await flushPromises()
}

function actionLabels() {
  return Array.from(document.body.querySelectorAll('.dropdown-item')).map(
    (item) => item.textContent?.trim() ?? '',
  )
}

async function chooseAction(label: string) {
  const item = Array.from(document.body.querySelectorAll('.dropdown-item')).find(
    (candidate) => candidate.textContent?.trim() === label,
  )
  item?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
  await flushPromises()
}

async function clickBuy(wrapper: VueWrapper) {
  await wrapper.get('[data-testid="holding-add-position"]').trigger('click')
  await flushPromises()
}

function withdrawLabel(row: HoldingRow) {
  return row.kind === 'FUNDS' ? 'Resgatar' : 'Vender / resgatar'
}

function stat(wrapper: VueWrapper, testId: string) {
  return wrapper.get(`[data-testid="${testId}"]`).text()
}

function control(wrapper: VueWrapper, testId: string) {
  const element = wrapper.find(`[data-testid="${testId}"]`)
  return ['INPUT', 'SELECT'].includes(element.element.tagName)
    ? element
    : element.find('input, select')
}

function modalButton(wrapper: VueWrapper, label: string) {
  return wrapper.findAll('.modal-card button').find((button) => button.text() === label)!
}

function modalNumberInputs(wrapper: VueWrapper) {
  return wrapper.findAll('.modal-card input[type="number"]')
}

describe('HoldingDrawer', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(holdingsApi.findAll).mockResolvedValue({
      content: [stockRow],
      page: { size: 500, number: 0, totalElements: 1, totalPages: 1 },
    })
  })

  afterEach(() => {
    activeWrapper?.unmount()
    activeWrapper = undefined
  })

  describe('sidebar', () => {
    it('opens as a right-hand overlay dialog labelled by the holding title', async () => {
      const wrapper = await mountLoadedDrawer(stockRow, stockDetailWithoutWithdrawals())

      const sidebar = wrapper.getComponent({ name: 'BSidebar' })
      expect(sidebar.props('modelValue')).toBe(true)
      expect(sidebar.props('right')).toBe(true)
      expect(sidebar.props('overlay')).toBe(true)
      expect(sidebar.props('mobile')).toBe('fullwidth')
      const panel = wrapper.get('[data-testid="holding-drawer"]')
      expect(panel.attributes('role')).toBe('dialog')
      expect(panel.attributes('aria-modal')).toBe('true')
      const title = wrapper.get('h2.drawer-title')
      expect(panel.attributes('aria-labelledby')).toBe(title.attributes('id'))
      expect(title.text()).toBe('PETR4')
    })

    it('moves focus to the close button when it opens', async () => {
      await mountLoadedDrawer(stockRow, stockDetailWithoutWithdrawals())

      expect(document.activeElement?.getAttribute('aria-label')).toBe('Fechar detalhe')
    })

    it('asks to close from the close button', async () => {
      const wrapper = await mountLoadedDrawer(stockRow, stockDetailWithoutWithdrawals())

      await wrapper.get('[data-drawer-close]').trigger('click')

      expect(wrapper.emitted('close')).toHaveLength(1)
    })

    it('asks to close when Buefy closes the sidebar', async () => {
      const wrapper = await mountLoadedDrawer(stockRow, stockDetailWithoutWithdrawals())

      wrapper.getComponent({ name: 'BSidebar' }).vm.$emit('close')

      expect(wrapper.emitted('close')).toHaveLength(1)
    })

    it('closes from a click on the backdrop but not from a click inside the panel', async () => {
      const wrapper = await mountLoadedDrawer(stockRow, stockDetailWithoutWithdrawals())

      await wrapper.get('[data-testid="drawer-stats"]').trigger('click')
      expect(wrapper.emitted('close')).toBeUndefined()

      await wrapper.get('.sidebar-background').trigger('click')
      expect(wrapper.emitted('close')).toHaveLength(1)
    })

    it('lets Escape close it only while no modal is open over it', async () => {
      const wrapper = await mountLoadedDrawer(stockRow, stockDetailWithoutWithdrawals())
      const sidebar = wrapper.getComponent({ name: 'BSidebar' })

      expect(sidebar.props('canCancel')).toEqual(['escape'])
      await clickBuy(wrapper)
      expect(sidebar.props('canCancel')).toEqual([])
      await modalButton(wrapper, 'Cancelar').trigger('click')
      await flushPromises()
      expect(sidebar.props('canCancel')).toEqual(['escape'])
    })
  })

  describe('header', () => {
    it('shows the ticker, the name, the type, the wallet and the segment of a stock', async () => {
      const wrapper = await mountLoadedDrawer(stockRow, {
        ...stockDetailWithoutWithdrawals(),
        segmentName: 'Energia',
      })

      expect(wrapper.get('.t-name').text()).toBe('Petróleo Brasileiro')
      expect(wrapper.get('[data-testid="drawer-type"]').text()).toBe('Ação PN')
      expect(wrapper.get('[data-testid="drawer-wallet"]').text()).toBe('Carteira B3')
      expect(wrapper.get('[data-testid="drawer-segment"]').text()).toBe('Energia')
    })

    it('keeps the header tags read-only', async () => {
      const wrapper = await mountLoadedDrawer(stockRow, stockDetailWithoutWithdrawals())

      const tags = ['drawer-type', 'drawer-wallet', 'drawer-segment'].map((testId) =>
        wrapper.get(`[data-testid="${testId}"]`),
      )
      expect(tags.map((tag) => tag.element.tagName)).toEqual(['SPAN', 'SPAN', 'SPAN'])
      expect(tags.some((tag) => tag.find('.icon').exists())).toBe(false)
    })

    it('falls back to the row segment, then to Sem segmento', async () => {
      const labelled = await mountLoadedDrawer(
        { ...stockRow, segmentLabel: 'Financeiro' },
        stockDetailWithoutWithdrawals(),
      )
      expect(labelled.get('[data-testid="drawer-segment"]').text()).toBe('Financeiro')
      labelled.unmount()

      const unlabelled = await mountLoadedDrawer(stockRow, stockDetailWithoutWithdrawals())
      expect(unlabelled.get('[data-testid="drawer-segment"]').text()).toBe('Sem segmento')
    })

    it('shows the current value, the result and the current price', async () => {
      const wrapper = await mountLoadedDrawer(stockRow, stockDetailWithoutWithdrawals())

      expect(wrapper.get('[data-testid="drawer-value"]').text()).toBe('R$ 6.960,00')
      expect(wrapper.get('[data-testid="drawer-gain"]').text()).toContain('+R$ 1.308,00')
      expect(wrapper.get('[data-testid="drawer-gain"]').text()).toContain('+23,10%')
      expect(wrapper.get('[data-testid="drawer-price"]').text()).toBe('R$ 34,80')
    })

    it('shows dashes for a missing value and price', async () => {
      const wrapper = await mountLoadedDrawer(
        { ...stockRow, currentValue: null, currentPrice: null, gain: null, gainPct: null },
        stockDetailWithoutWithdrawals(),
      )

      expect(wrapper.get('[data-testid="drawer-value"]').text()).toBe('—')
      expect(wrapper.get('[data-testid="drawer-price"]').text()).toBe('—')
    })

    it('shows a fund by name, with its segment and no current price', async () => {
      const wrapper = await mountLoadedDrawer(fundRow, {
        ...fundDetail(),
        segmentName: 'Renda fixa pública',
      })

      expect(wrapper.get('h2.drawer-title').text()).toBe('Tesouro IPCA+')
      expect(wrapper.get('.t-name').text()).toBe('Fundo de investimento')
      expect(wrapper.get('[data-testid="drawer-segment"]').text()).toBe('Renda fixa pública')
      expect(wrapper.find('[data-testid="drawer-price"]').exists()).toBe(false)
    })

    it('shows the type and the segment of a crypto holding, with Sem segmento as the fallback', async () => {
      const typedRow = { ...cryptoRow, typeLabel: 'Moeda' }
      const wrapper = await mountLoadedDrawer(typedRow, {
        ...cryptoDetail(),
        typeId: 'crypto-type-1',
        typeName: 'Moeda',
        segmentId: 'segment-1',
        segmentName: 'Camada 1',
      })

      expect(wrapper.get('[data-testid="drawer-type"]').text()).toBe('Moeda')
      expect(wrapper.get('[data-testid="drawer-segment"]').text()).toBe('Camada 1')
      wrapper.unmount()

      const untyped = await mountLoadedDrawer(cryptoRow, cryptoDetail())
      expect(untyped.get('[data-testid="drawer-type"]').text()).toBe('Cripto')
      expect(untyped.get('[data-testid="drawer-segment"]').text()).toBe('Sem segmento')
    })

    it('keeps the wallet currency when it is not asked to convert', async () => {
      const wrapper = mountDrawer(
        { ...stockRow, walletCurrency: 'USD' },
        { convertToDisplayCurrency: false },
      )
      vi.mocked(holdingsApi.getStockHolding).mockResolvedValue(stockDetailWithoutWithdrawals())
      await flushPromises()

      expect(wrapper.get('[data-testid="drawer-value"]').text()).toBe('US$ 6.960,00')
      expect(useCurrencyStore().convert).not.toHaveBeenCalledWith(6960, 'USD')
    })

    it('converts the header figures into the display currency when asked to', async () => {
      vi.mocked(holdingsApi.getStockHolding).mockResolvedValue(stockDetailWithoutWithdrawals())
      const wrapper = mountDrawer({ ...stockRow, walletCurrency: 'USD' }, { conversionRate: 5 })
      await flushPromises()

      expect(wrapper.get('[data-testid="drawer-price"]').text()).toBe('R$ 174,00')
      expect(wrapper.get('[data-testid="drawer-value"]').text()).toBe('R$ 34.800,00')
      expect(wrapper.get('[data-testid="drawer-gain"]').text()).toContain('+R$ 6.540,00')
    })
  })

  describe('lazy detail fetch', () => {
    it.each(kindCases)(
      'fetches the $label holding once from its own endpoint using the row wallet and id',
      async ({ row, detail, getHolding }) => {
        getHolding.mockResolvedValue(detail())

        mountDrawer(row)
        await flushPromises()

        expect(getHolding).toHaveBeenCalledTimes(1)
        expect(getHolding).toHaveBeenCalledWith(row.walletId, row.id)
        for (const other of kindCases.filter((kindCase) => kindCase.row.kind !== row.kind)) {
          expect(other.getHolding).not.toHaveBeenCalled()
        }
      },
    )

    it('shows the header but neither the stats, the movements nor the actions while the fetch is in flight', async () => {
      vi.mocked(holdingsApi.getStockHolding).mockReturnValue(new Promise(() => undefined))

      const wrapper = mountDrawer(stockRow)
      await flushPromises()

      expect(wrapper.get('h2.drawer-title').text()).toBe('PETR4')
      expect(wrapper.find('[data-testid="drawer-stats"]').exists()).toBe(false)
      expect(wrapper.find('.ledger-list').exists()).toBe(false)
      expect(wrapper.find('[data-testid="holding-actions"]').exists()).toBe(false)
    })

    it('renders the stats, the movements and the actions once the fetch resolves', async () => {
      const wrapper = await mountLoadedDrawer(stockRow, stockDetailWithoutWithdrawals())

      expect(wrapper.find('[data-testid="drawer-stats"]').exists()).toBe(true)
      expect(wrapper.find('.ledger-list').exists()).toBe(true)
      expect(wrapper.find('[data-testid="holding-actions"]').exists()).toBe(true)
    })

    it('keeps only the header when the fetch fails', async () => {
      vi.mocked(holdingsApi.getStockHolding).mockRejectedValue(new Error('500'))

      const wrapper = mountDrawer(stockRow)
      await flushPromises()

      expect(wrapper.get('h2.drawer-title').text()).toBe('PETR4')
      expect(wrapper.find('.ledger-list').exists()).toBe(false)
      expect(wrapper.find('[data-testid="holding-actions"]').exists()).toBe(false)
    })
  })

  describe('stats', () => {
    it('shows quantity, average price, invested amount and first purchase of a stock', async () => {
      const wrapper = await mountLoadedDrawer(stockRow, stockDetailWithWithdrawals())

      expect(stat(wrapper, 'stat-quantity')).toBe('200')
      expect(stat(wrapper, 'stat-average-price')).toBe('R$ 28,26')
      expect(stat(wrapper, 'stat-invested')).toBe('R$ 5.652,00')
      expect(stat(wrapper, 'stat-first-movement')).toBe('12 jan 2026')
    })

    it('sums the profit of every exit as the realised result', async () => {
      const detail = stockDetailWithWithdrawals()
      detail.withdrawals[1] = { ...detail.withdrawals[1], resultType: 'REINVESTMENT' }

      const wrapper = await mountLoadedDrawer(stockRow, detail)

      expect(stat(wrapper, 'stat-realized-result')).toBe('+R$ 646,00')
    })

    it('shows a dash as the realised result before any exit', async () => {
      const wrapper = await mountLoadedDrawer(stockRow, stockDetailWithoutWithdrawals())

      expect(stat(wrapper, 'stat-realized-result')).toBe('—')
    })

    it('shows dashes for the quantity and the average price of a stock without a quantity', async () => {
      const wrapper = await mountLoadedDrawer(
        { ...stockRow, quantity: null },
        stockDetailWithoutWithdrawals(),
      )

      expect(stat(wrapper, 'stat-quantity')).toBe('—')
      expect(stat(wrapper, 'stat-average-price')).toBe('—')
    })

    it('shows a dash as the first purchase when there is none', async () => {
      const wrapper = await mountLoadedDrawer(stockRow, {
        ...stockDetailWithoutWithdrawals(),
        lots: [],
      })

      expect(stat(wrapper, 'stat-first-movement')).toBe('—')
    })

    it('shows the invested amount, the first contribution and the fee rates of a fund', async () => {
      const wrapper = await mountLoadedDrawer(fundRow, fundDetail())

      const stats = wrapper.get('[data-testid="drawer-stats"]').text()
      expect(stats).toContain('Investido')
      expect(stats).toContain('Primeiro aporte')
      expect(stats).toContain('Taxa de administração (% a.a.)')
      expect(stats).toContain('Taxa de performance (%)')
      expect(stats).not.toContain('Preço médio')
      expect(stat(wrapper, 'stat-invested')).toBe('R$ 3.000,00')
      expect(stat(wrapper, 'stat-first-movement')).toBe('10 jan 2026')
    })

    it.each(kindCases)(
      'shows the fee rates only for a fund, not for a $label holding',
      async ({ row, detail }) => {
        const wrapper = await mountLoadedDrawer(row, detail())

        expect(wrapper.find('[data-testid="administration-fee-rate"]').exists()).toBe(
          row.kind === 'FUNDS',
        )
      },
    )

    it('shows an em dash for each rate that is null', async () => {
      const wrapper = await mountLoadedDrawer(fundRow, fundDetail())

      expect(stat(wrapper, 'administration-fee-rate')).toBe('—')
      expect(stat(wrapper, 'performance-fee-rate')).toBe('—')
    })

    it('shows both rates as percentages', async () => {
      const wrapper = await mountLoadedDrawer(
        fundRow,
        fundDetail({ administrationFeeRate: 1.5, performanceFeeRate: 20 }),
      )

      expect(stat(wrapper, 'administration-fee-rate')).toBe('1,50%')
      expect(stat(wrapper, 'performance-fee-rate')).toBe('20,00%')
    })

    it('shows a rate of zero as 0,00% rather than a dash', async () => {
      const wrapper = await mountLoadedDrawer(
        fundRow,
        fundDetail({ administrationFeeRate: 0, performanceFeeRate: null }),
      )

      expect(stat(wrapper, 'administration-fee-rate')).toBe('0,00%')
      expect(stat(wrapper, 'performance-fee-rate')).toBe('—')
    })

    it('reflects the rates saved from the update modal after the detail reloads', async () => {
      vi.mocked(holdingsApi.updateFundHolding).mockResolvedValue(fundDetail())
      const wrapper = await mountLoadedDrawer(fundRow, fundDetail())
      vi.mocked(holdingsApi.getFundHolding).mockResolvedValue(
        fundDetail({ administrationFeeRate: 2, performanceFeeRate: 10 }),
      )

      await chooseAction('Atualizar valor atual')
      const [, administrationInput, performanceInput] = modalNumberInputs(wrapper)
      await administrationInput.setValue('2')
      await performanceInput.setValue('10')
      await modalButton(wrapper, 'Salvar').trigger('click')
      await flushPromises()

      expect(holdingsApi.updateFundHolding).toHaveBeenCalledWith('wallet-2', 'holding-2', {
        currentValue: 3000,
        administrationFeeRate: 2,
        performanceFeeRate: 10,
      })
      expect(stat(wrapper, 'administration-fee-rate')).toBe('2,00%')
      expect(stat(wrapper, 'performance-fee-rate')).toBe('10,00%')
    })
  })

  describe('movements', () => {
    it('lists a holding with no withdrawals', async () => {
      const wrapper = await mountLoadedDrawer(stockRow, stockDetailWithoutWithdrawals())

      const items = ledgerItems(wrapper)
      expect(items).toHaveLength(1)
      expect(items[0].text()).toContain('Compra')
      expect(items[0].text()).toContain('+300')
      expect(wrapper.text()).not.toContain('Venda')
    })

    it('counts the movements in the header', async () => {
      const wrapper = await mountLoadedDrawer(stockRow, stockDetailWithWithdrawals())

      expect(wrapper.find('.ledger-title').text()).toBe('Movimentações')
      expect(wrapper.find('.ledger-count').text()).toBe('4')
    })

    it('merges purchases and withdrawals into one chronological list with signed quantities', async () => {
      const wrapper = await mountLoadedDrawer(stockRow, stockDetailWithWithdrawals())

      const items = ledgerItems(wrapper)
      expect(items).toHaveLength(4)
      expect(items[0].text()).toContain('Compra')
      expect(items[0].text()).toContain('+300')
      expect(items[2].text()).toContain('Venda')
      expect(items[2].text()).toContain('−150')
      expect(deleteButton(items[0]).exists()).toBe(true)
      expect(deleteButton(items[2]).exists()).toBe(true)
    })

    it('shows the unit price and the amount of a purchase, with no result', async () => {
      const wrapper = await mountLoadedDrawer(stockRow, stockDetailWithoutWithdrawals())

      const item = ledgerItems(wrapper)[0]
      expect(item.get('[data-testid="ledger-detail"]').text()).toBe('+300 × R$ 25,10')
      expect(item.get('[data-testid="ledger-amount"]').text()).toBe('R$ 7.530,00')
      expect(item.find('[data-testid="ledger-result"]').exists()).toBe(false)
    })

    it('shows the costs, the net amount and the signed profit of a withdrawal', async () => {
      const wrapper = await mountLoadedDrawer(stockRow, stockDetailWithWithdrawals())

      const item = ledgerItems(wrapper)[2]
      expect(item.get('[data-testid="ledger-detail"]').text()).toBe('−150 × R$ 30,50')
      expect(item.get('[data-testid="ledger-costs"]').text()).toBe('taxas + impostos R$ 130,00')
      expect(item.get('[data-testid="ledger-amount"]').text()).toBe('R$ 4.445,00')
      expect(item.get('[data-testid="ledger-result"]').text()).toBe('+R$ 206,00')
    })

    it('lists a crypto holding like a stock', async () => {
      const wrapper = await mountLoadedDrawer(cryptoRow, cryptoDetail())

      const item = ledgerItems(wrapper)[0]
      expect(item.text()).toContain('Compra')
      expect(item.get('[data-testid="ledger-detail"]').text()).toBe('+0,5 × R$ 200.000,00')
    })

    it('words a fund movement as Aporte and Resgate and describes it by its amount', async () => {
      const wrapper = await mountLoadedDrawer(fundRow, fundDetail())

      const items = ledgerItems(wrapper)
      expect(items.map((item) => item.get('.ledger-tag').text())).toEqual(['Aporte', 'Resgate'])
      expect(items[0].get('[data-testid="ledger-detail"]').text()).toBe('Valor aportado')
      expect(items[0].get('[data-testid="ledger-amount"]').text()).toBe('R$ 4.000,00')
      expect(items[1].get('[data-testid="ledger-result"]').text()).toBe('−R$ 15,00')
    })

    it('shows a loss as a negative result', async () => {
      const detail = stockDetailWithWithdrawals()
      detail.withdrawals[0] = withdrawalOf({ profit: -80 })

      const wrapper = await mountLoadedDrawer(stockRow, detail)

      expect(ledgerItems(wrapper)[2].get('[data-testid="ledger-result"]').text()).toBe('−R$ 80,00')
    })

    it('tags a reinvestment as Reinvestimento and offers no undo for it', async () => {
      const detail = stockDetailWithWithdrawals()
      detail.withdrawals[1] = { ...detail.withdrawals[1], resultType: 'REINVESTMENT' }

      const wrapper = await mountLoadedDrawer(stockRow, detail)

      const reinvestmentItem = ledgerItems(wrapper)[3]
      expect(reinvestmentItem.text()).toContain('Reinvestimento')
      expect(deleteButton(reinvestmentItem).exists()).toBe(false)
      expect(deleteButton(ledgerItems(wrapper)[2]).exists()).toBe(true)
    })

    it('describes a withdrawal without a quantity by its amount', async () => {
      const detail = stockDetailWithWithdrawals()
      detail.withdrawals[0] = withdrawalOf({ quantity: null })

      const wrapper = await mountLoadedDrawer(stockRow, detail)

      expect(ledgerItems(wrapper)[2].get('[data-testid="ledger-detail"]').text()).toBe(
        'Valor resgatado',
      )
    })

    it('shows the amounts in the display currency', async () => {
      vi.mocked(holdingsApi.getStockHolding).mockResolvedValue(stockDetailWithoutWithdrawals())
      const wrapper = mountDrawer({ ...stockRow, walletCurrency: 'USD' }, { conversionRate: 5 })
      await flushPromises()

      expect(useCurrencyStore().convert).toHaveBeenCalledWith(25.1, 'USD')
      expect(ledgerItems(wrapper)[0].get('[data-testid="ledger-detail"]').text()).toBe(
        '+300 × R$ 125,50',
      )
    })

    it('offers no delete buttons to a non-admin', async () => {
      const wrapper = await mountLoadedDrawer(stockRow, stockDetailWithWithdrawals(), {
        isAdmin: false,
      })

      expect(wrapper.findAll('[data-testid="ledger-delete"]')).toHaveLength(0)
    })
  })

  describe('tags', () => {
    it.each(kindCases)('offers Editar tags on a $label holding', async ({ row, detail }) => {
      await mountLoadedDrawer(row, detail())

      expect(actionLabels()).toContain('Editar tags')
    })

    it('edits a stock segment preselected and reloads the detail after saving', async () => {
      vi.mocked(holdingsApi.updateHoldingSegment).mockResolvedValue(stockDetailWithoutWithdrawals())
      const wrapper = await mountLoadedDrawer(stockRow, {
        ...stockDetailWithoutWithdrawals(),
        segmentId: 'segment-1',
        segmentName: 'Energia',
      })

      useTypesListStore().holdingTypes = [
        { id: 'type-1', name: 'Ação PN', kinds: ['STOCKS'], usageCount: 1 },
      ]
      useTypesListStore().segments = [{ id: 'segment-1', name: 'Energia', usageCount: 1 }]
      await chooseAction('Editar tags')
      const typeSelect = wrapper.find('select[data-testid="edit-tags-type"]')
      expect((typeSelect.element as HTMLSelectElement).value).toBe('type-1')
      const segmentSelect = wrapper.find('select[data-testid="edit-tags-segment"]')
      expect((segmentSelect.element as HTMLSelectElement).value).toBe('segment-1')
      await segmentSelect.setValue('')
      await modalButton(wrapper, 'Salvar').trigger('click')
      await flushPromises()

      expect(holdingsApi.updateHoldingSegment).toHaveBeenCalledWith(
        'STOCKS',
        'wallet-1',
        'holding-1',
        null,
      )
      expect(holdingsApi.updateStockHolding).not.toHaveBeenCalled()
      expect(holdingsApi.getStockHolding).toHaveBeenCalledTimes(2)
      expect(wrapper.emitted('positionAdded')).toHaveLength(1)
    })

    it('edits a fund type and reloads the detail after saving', async () => {
      vi.mocked(holdingsApi.updateFundHolding).mockResolvedValue(fundDetail())
      const wrapper = await mountLoadedDrawer(fundRow, fundDetail())

      useTypesListStore().holdingTypes = [
        { id: 'type-2', name: 'Renda fixa', kinds: ['FUNDS'], usageCount: 1 },
        { id: 'type-3', name: 'Multimercado', kinds: ['FUNDS'], usageCount: 0 },
      ]
      await chooseAction('Editar tags')
      const typeSelect = wrapper.find('select[data-testid="edit-tags-type"]')
      expect((typeSelect.element as HTMLSelectElement).value).toBe('type-2')
      await typeSelect.setValue('type-3')
      await modalButton(wrapper, 'Salvar').trigger('click')
      await flushPromises()

      expect(holdingsApi.updateFundHolding).toHaveBeenCalledWith(fundRow.walletId, fundRow.id, {
        typeId: 'type-3',
      })
      expect(holdingsApi.getFundHolding).toHaveBeenCalledTimes(2)
      expect(wrapper.emitted('positionAdded')).toHaveLength(1)
    })

    it('edits the type and the segment of a crypto holding without a type yet', async () => {
      vi.mocked(holdingsApi.updateCryptoHolding).mockResolvedValue(cryptoDetail())
      vi.mocked(holdingsApi.updateHoldingSegment).mockResolvedValue(cryptoDetail())
      const wrapper = await mountLoadedDrawer(cryptoRow, cryptoDetail())

      useTypesListStore().holdingTypes = [
        { id: 'type-4', name: 'Moeda', kinds: ['CRYPTO'], usageCount: 0 },
        { id: 'type-1', name: 'Ação PN', kinds: ['STOCKS'], usageCount: 1 },
      ]
      useTypesListStore().segments = [{ id: 'segment-1', name: 'Camada 1', usageCount: 0 }]
      await chooseAction('Editar tags')
      const typeSelect = wrapper.find('select[data-testid="edit-tags-type"]')
      expect(typeSelect.findAll('option').map((option) => option.text())).toEqual([
        'Sem tipo',
        'Moeda',
      ])
      await typeSelect.setValue('type-4')
      await wrapper.find('select[data-testid="edit-tags-segment"]').setValue('segment-1')
      await modalButton(wrapper, 'Salvar').trigger('click')
      await flushPromises()

      expect(holdingsApi.updateCryptoHolding).toHaveBeenCalledWith(
        cryptoRow.walletId,
        cryptoRow.id,
        { typeId: 'type-4' },
      )
      expect(holdingsApi.updateHoldingSegment).toHaveBeenCalledWith(
        'CRYPTO',
        cryptoRow.walletId,
        cryptoRow.id,
        'segment-1',
      )
      expect(holdingsApi.getCryptoHolding).toHaveBeenCalledTimes(2)
      expect(wrapper.emitted('positionAdded')).toHaveLength(1)
    })
  })

  describe('freezing', () => {
    function frozenOf(row: HoldingRow): HoldingRow {
      return { ...row, frozen: true }
    }

    it.each(kindCases)(
      'shows no snowflake and offers Congelar on an open $label holding',
      async ({ row, detail }) => {
        const wrapper = await mountLoadedDrawer(row, detail())

        expect(wrapper.find('[data-testid="frozen-tag"]').exists()).toBe(false)
        expect(wrapper.find('.frozen-note').exists()).toBe(false)
        expect(actionLabels()).toContain('Congelar')
        expect(actionLabels()).not.toContain('Descongelar')
      },
    )

    it.each(kindCases)(
      'swaps the badge for the snowflake, notes the freeze and offers Descongelar on a frozen $label holding',
      async ({ row, detail }) => {
        const wrapper = await mountLoadedDrawer(frozenOf(row), detail())

        const badge = wrapper.get('[data-testid="frozen-tag"]')
        expect(badge.attributes('aria-label')).toBe('Congelado')
        expect(badge.find('.mdi-snowflake').exists()).toBe(true)
        expect(wrapper.get('.frozen-note').text()).toBe(
          'Posição congelada: novas compras ficam bloqueadas.',
        )
        expect(actionLabels()).toContain('Descongelar')
        expect(actionLabels()).not.toContain('Congelar')
      },
    )

    it.each(kindCases)(
      'keeps the buy button enabled on an open $label holding',
      async ({ row, detail }) => {
        const wrapper = await mountLoadedDrawer(row, detail())

        const buyButton = wrapper.get('[data-testid="holding-add-position"]')
        expect(buyButton.text()).toBe(row.kind === 'FUNDS' ? 'Novo aporte' : 'Nova compra')
        expect(buyButton.attributes('disabled')).toBeUndefined()
      },
    )

    it.each(kindCases)(
      'disables the buy button and opens no modal on a frozen $label holding',
      async ({ row, detail }) => {
        const wrapper = await mountLoadedDrawer(frozenOf(row), detail())

        const buyButton = wrapper.get('[data-testid="holding-add-position"]')
        expect(buyButton.attributes('disabled')).toBeDefined()
        await clickBuy(wrapper)

        expect(document.body.querySelector('.modal-card')).toBeNull()
      },
    )

    it.each(kindCases)(
      'freezes an open $label holding, reloads its detail and reports the change',
      async ({ row, detail, updateHolding, getHolding }) => {
        updateHolding.mockResolvedValue(detail())
        const wrapper = await mountLoadedDrawer(row, detail())

        await chooseAction('Congelar')

        expect(updateHolding).toHaveBeenCalledWith(row.walletId, row.id, { frozen: true })
        expect(getHolding).toHaveBeenCalledTimes(2)
        expect(wrapper.emitted('positionAdded')).toHaveLength(1)
      },
    )

    it.each(kindCases)(
      'unfreezes a frozen $label holding',
      async ({ row, detail, updateHolding, getHolding }) => {
        updateHolding.mockResolvedValue(detail())
        const wrapper = await mountLoadedDrawer(frozenOf(row), detail())

        await chooseAction('Descongelar')

        expect(updateHolding).toHaveBeenCalledWith(row.walletId, row.id, { frozen: false })
        expect(getHolding).toHaveBeenCalledTimes(2)
        expect(wrapper.emitted('positionAdded')).toHaveLength(1)
      },
    )

    it('lets a non-admin freeze a holding', async () => {
      vi.mocked(holdingsApi.updateStockHolding).mockResolvedValue(stockDetailWithoutWithdrawals())
      await mountLoadedDrawer(stockRow, stockDetailWithoutWithdrawals(), { isAdmin: false })

      await chooseAction('Congelar')

      expect(holdingsApi.updateStockHolding).toHaveBeenCalledWith('wallet-1', 'holding-1', {
        frozen: true,
      })
    })

    it('reports nothing and keeps the drawer as it was when the toggle is rejected', async () => {
      vi.mocked(holdingsApi.updateStockHolding).mockRejectedValue(new Error('500'))
      const wrapper = await mountLoadedDrawer(stockRow, stockDetailWithoutWithdrawals())

      await chooseAction('Congelar')

      expect(holdingsApi.getStockHolding).toHaveBeenCalledTimes(1)
      expect(wrapper.emitted('positionAdded')).toBeUndefined()
    })

    it('keeps every other action available on a frozen holding', async () => {
      const wrapper = await mountLoadedDrawer(frozenOf(stockRow), stockDetailWithoutWithdrawals())

      expect(actionLabels()).toEqual([
        'Vender / resgatar',
        'Reinvestir',
        'Mover para outra carteira',
        'Atualizar preço',
        'Editar tags',
        'Descongelar',
        'Remover investimento',
      ])
      await chooseAction('Atualizar preço')
      expect(wrapper.find('.modal-card-title').exists()).toBe(true)
    })
  })

  describe('purchase removal', () => {
    it.each(kindCases)(
      'removes a $label entry on confirm, reloads the detail and reports the change',
      async ({ row, detail, getHolding, deleteEntry, entryId }) => {
        deleteEntry.mockResolvedValue(undefined)
        const wrapper = await mountLoadedDrawer(row, detail())

        await deleteButton(ledgerItems(wrapper)[0]).trigger('click')
        await confirmDialog('Remover')

        expect(deleteEntry).toHaveBeenCalledWith(row.walletId, row.id, entryId)
        expect(getHolding).toHaveBeenCalledTimes(2)
        expect(wrapper.emitted('positionAdded')).toHaveLength(1)
        expect(wrapper.emitted('close')).toBeUndefined()
      },
    )

    it('words the stock dialog and toast as a purchase', async () => {
      vi.mocked(holdingsApi.deleteStockLot).mockResolvedValue(undefined)
      const wrapper = await mountLoadedDrawer(stockRow, stockDetailWithoutWithdrawals())

      await deleteButton(ledgerItems(wrapper)[0]).trigger('click')
      await flushPromises()
      expect(document.body.textContent).toContain('Remover compra')
      await confirmDialog('Remover')

      expect(document.body.textContent).toContain('Compra removida.')
    })

    it('words the fund dialog and toast as a contribution', async () => {
      vi.mocked(holdingsApi.deleteFundContribution).mockResolvedValue(undefined)
      const wrapper = await mountLoadedDrawer(fundRow, fundDetail())

      await deleteButton(ledgerItems(wrapper)[0]).trigger('click')
      await flushPromises()
      expect(document.body.textContent).toContain('Remover aporte')
      await confirmDialog('Remover')

      expect(document.body.textContent).toContain('Aporte removido.')
    })

    it('keeps everything as it was when the dialog is cancelled', async () => {
      const wrapper = await mountLoadedDrawer(stockRow, stockDetailWithoutWithdrawals())

      await deleteButton(ledgerItems(wrapper)[0]).trigger('click')
      await confirmDialog('Cancelar')

      expect(holdingsApi.deleteStockLot).not.toHaveBeenCalled()
      expect(holdingsApi.getStockHolding).toHaveBeenCalledTimes(1)
      expect(wrapper.emitted('positionAdded')).toBeUndefined()
    })
  })

  describe('withdrawal undo', () => {
    it('undoes the most recent withdrawal on confirm', async () => {
      vi.mocked(resultsApi.deleteStockWithdrawal).mockResolvedValue(undefined)
      const wrapper = await mountLoadedDrawer(stockRow, stockDetailWithWithdrawals())

      await deleteButton(ledgerItems(wrapper)[3]).trigger('click')
      await confirmDialog('Desfazer')

      expect(resultsApi.deleteStockWithdrawal).toHaveBeenCalledWith('wallet-1', 'holding-1', 'w-2')
      expect(holdingsApi.getStockHolding).toHaveBeenCalledTimes(2)
      expect(wrapper.emitted('positionAdded')).toHaveLength(1)
    })

    it('words the stock dialog and toast as a sale', async () => {
      vi.mocked(resultsApi.deleteStockWithdrawal).mockResolvedValue(undefined)
      const wrapper = await mountLoadedDrawer(stockRow, stockDetailWithWithdrawals())

      await deleteButton(ledgerItems(wrapper)[3]).trigger('click')
      await flushPromises()
      expect(document.body.textContent).toContain('Desfazer venda')
      await confirmDialog('Desfazer')

      expect(document.body.textContent).toContain('Venda desfeita.')
    })

    it('undoes a crypto sale through the crypto endpoint', async () => {
      vi.mocked(resultsApi.deleteCryptoWithdrawal).mockResolvedValue(undefined)
      const detail = cryptoDetail()
      detail.withdrawals = [withdrawalOf({ id: 'cw-1', resultDate: '2026-04-01', quantity: 0.1 })]
      const wrapper = await mountLoadedDrawer(cryptoRow, detail)

      await deleteButton(ledgerItems(wrapper)[1]).trigger('click')
      await confirmDialog('Desfazer')

      expect(resultsApi.deleteCryptoWithdrawal).toHaveBeenCalledWith(
        'wallet-3',
        'holding-3',
        'cw-1',
      )
      expect(holdingsApi.getCryptoHolding).toHaveBeenCalledTimes(2)
    })

    it('words a fund withdrawal as a redemption and undoes it through the fund endpoint', async () => {
      vi.mocked(resultsApi.deleteFundWithdrawal).mockResolvedValue(undefined)
      const wrapper = await mountLoadedDrawer(fundRow, fundDetail())

      await deleteButton(ledgerItems(wrapper)[1]).trigger('click')
      await flushPromises()
      expect(document.body.textContent).toContain('Desfazer resgate')
      await confirmDialog('Desfazer')

      expect(resultsApi.deleteFundWithdrawal).toHaveBeenCalledWith('wallet-2', 'holding-2', 'fw-1')
      expect(document.body.textContent).toContain('Resgate desfeito.')
    })

    it('lets the server reject undoing a withdrawal that is not the most recent, without reloading', async () => {
      vi.mocked(resultsApi.deleteStockWithdrawal).mockRejectedValue(new Error('409 Conflict'))
      const wrapper = await mountLoadedDrawer(stockRow, stockDetailWithWithdrawals())

      await deleteButton(ledgerItems(wrapper)[2]).trigger('click')
      await confirmDialog('Desfazer')

      expect(resultsApi.deleteStockWithdrawal).toHaveBeenCalledWith('wallet-1', 'holding-1', 'w-1')
      expect(holdingsApi.getStockHolding).toHaveBeenCalledTimes(1)
      expect(wrapper.emitted('positionAdded')).toBeUndefined()
    })
  })

  describe('editing a purchase or contribution', () => {
    function rejectionOf(detail: string) {
      return Object.assign(new Error('400'), {
        isAxiosError: true,
        response: { status: 400, data: { detail } },
      })
    }

    async function openEdit(wrapper: VueWrapper, index = 0) {
      await ledgerItems(wrapper)[index].get('[data-testid="ledger-edit"]').trigger('click')
      await flushPromises()
    }

    it('shows dates as plain text and no inline date editor', async () => {
      const wrapper = await mountLoadedDrawer(stockRow, stockDetailWithWithdrawals())

      const items = ledgerItems(wrapper)
      expect(items[0].get('[data-testid="ledger-date"]').text()).toBe('12 jan 2026')
      expect(items[2].get('[data-testid="ledger-date"]').text()).toBe('10 mai 2026')
      expect(wrapper.find('button.date-edit').exists()).toBe(false)
      expect(items[2].find('[data-testid="ledger-edit"]').exists()).toBe(false)
    })

    it.each(kindCases)(
      'opens the add dialog prefilled for a $label entry and saves the correction',
      async ({ row, detail, getHolding, updateEntry, entryId, dateField }) => {
        updateEntry.mockResolvedValue(undefined)
        const wrapper = await mountLoadedDrawer(row, detail())

        await openEdit(wrapper)

        const isFund = row.kind === 'FUNDS'
        expect(wrapper.find('.modal-card-title').text()).toBe(
          isFund ? 'Editar aporte' : 'Editar compra',
        )
        expect(wrapper.find('.modal-card-body').text()).toContain(
          `${row.ticker ?? row.name} · ${row.walletName} · registrada em`,
        )
        const inputs = modalNumberInputs(wrapper)
        const save = modalButton(wrapper, 'Salvar alterações')
        expect(save.attributes('disabled')).toBeDefined()

        await inputs[0].setValue(isFund ? '4500' : '3')
        await flushPromises()
        expect(wrapper.find('[data-testid="position-impact"]').text()).toContain('Antes:')
        await save.trigger('click')
        await flushPromises()

        const lot = isFund ? null : (detail() as StockHoldingDetail | CryptoHoldingDetail).lots[0]
        expect(updateEntry).toHaveBeenCalledWith(
          row.walletId,
          row.id,
          entryId,
          isFund
            ? { [dateField]: '2026-01-10', amount: 4500 }
            : { [dateField]: lot!.lotDate, quantity: 3, price: lot!.price },
        )
        expect(getHolding).toHaveBeenCalledTimes(2)
        expect(wrapper.emitted('positionAdded')).toHaveLength(1)
        expect(wrapper.find('.modal-card').exists()).toBe(false)
        expect(document.body.textContent).toContain(
          isFund ? 'Aporte atualizado!' : 'Compra atualizada!',
        )
      },
    )

    it('keeps the dialog open with the server message when the correction is rejected', async () => {
      vi.mocked(holdingsApi.updateStockLot).mockRejectedValue(
        rejectionOf('A quantidade das compras não pode ficar abaixo da quantidade já vendida'),
      )
      const wrapper = await mountLoadedDrawer(stockRow, stockDetailWithWithdrawals())

      await openEdit(wrapper)
      await modalNumberInputs(wrapper)[0].setValue('10')
      await modalButton(wrapper, 'Salvar alterações').trigger('click')
      await flushPromises()

      expect(wrapper.find('[data-testid="position-error"]').text()).toContain(
        'não pode ficar abaixo da quantidade já vendida',
      )
      expect(wrapper.find('.modal-card').exists()).toBe(true)
      expect(holdingsApi.getStockHolding).toHaveBeenCalledTimes(1)
      expect(wrapper.emitted('positionAdded')).toBeUndefined()
    })

    it('discards the changes when the dialog is cancelled', async () => {
      const wrapper = await mountLoadedDrawer(stockRow, stockDetailWithoutWithdrawals())

      await openEdit(wrapper)
      await modalNumberInputs(wrapper)[0].setValue('10')
      await modalButton(wrapper, 'Cancelar').trigger('click')
      await flushPromises()

      expect(wrapper.find('.modal-card').exists()).toBe(false)
      expect(holdingsApi.updateStockLot).not.toHaveBeenCalled()
      expect(wrapper.emitted('close')).toBeUndefined()
    })

    it('lets a non-admin edit a purchase', async () => {
      const wrapper = await mountLoadedDrawer(stockRow, stockDetailWithoutWithdrawals(), {
        isAdmin: false,
      })

      await openEdit(wrapper)

      expect(wrapper.find('.modal-card-title').text()).toBe('Editar compra')
    })

    it('lets a purchase of a frozen holding be corrected', async () => {
      const wrapper = await mountLoadedDrawer(
        { ...stockRow, frozen: true },
        stockDetailWithoutWithdrawals(),
      )

      await openEdit(wrapper)

      expect(wrapper.find('.modal-card-title').text()).toBe('Editar compra')
    })

    it('opens the add dialog empty again after an edit was closed', async () => {
      const wrapper = await mountLoadedDrawer(stockRow, stockDetailWithoutWithdrawals())

      await openEdit(wrapper)
      await modalButton(wrapper, 'Cancelar').trigger('click')
      await flushPromises()
      await clickBuy(wrapper)

      expect(wrapper.find('.modal-card-title').text()).toBe('Registrar compra')
      expect((modalNumberInputs(wrapper)[0].element as HTMLInputElement).value).toBe('')
    })
  })

  describe('actions', () => {
    it('shows the buy action as its own button and every other action behind Mais ações', async () => {
      const wrapper = await mountLoadedDrawer(stockRow, stockDetailWithWithdrawals())

      expect(wrapper.get('[data-testid="holding-add-position"]').text()).toBe('Nova compra')
      expect(wrapper.find('[data-testid="holding-actions"] button').text()).toBe('Mais ações')
      expect(actionLabels()).toEqual([
        'Vender / resgatar',
        'Reinvestir',
        'Mover para outra carteira',
        'Atualizar preço',
        'Editar tags',
        'Congelar',
        'Remover investimento',
      ])
    })

    it('words the crypto actions as purchases and prices', async () => {
      const wrapper = await mountLoadedDrawer(cryptoRow, cryptoDetail())

      expect(wrapper.get('[data-testid="holding-add-position"]').text()).toBe('Nova compra')
      expect(actionLabels()).toContain('Atualizar preço')
      expect(actionLabels()).toContain('Vender / resgatar')
    })

    it('uses the fund wording and hides Remover from a non-admin', async () => {
      const wrapper = await mountLoadedDrawer(fundRow, fundDetail(), { isAdmin: false })

      expect(wrapper.get('[data-testid="holding-add-position"]').text()).toBe('Novo aporte')
      expect(actionLabels()).toEqual([
        'Resgatar',
        'Reinvestir',
        'Mover para outra carteira',
        'Atualizar valor atual',
        'Editar tags',
        'Congelar',
      ])
    })

    describe('register purchase', () => {
      it('records a stock purchase, reloads the detail and reports the change', async () => {
        vi.mocked(holdingsApi.addStockLot).mockResolvedValue(newLot)
        const wrapper = await mountLoadedDrawer(stockRow, stockDetailWithoutWithdrawals())

        await clickBuy(wrapper)
        expect(wrapper.find('.modal-card-title').text()).toBe('Registrar compra')
        const [quantityInput, priceInput] = modalNumberInputs(wrapper)
        await quantityInput.setValue('10')
        await priceInput.setValue('30')
        await modalButton(wrapper, 'Registrar compra').trigger('click')
        await flushPromises()

        expect(holdingsApi.addStockLot).toHaveBeenCalledWith(
          'wallet-1',
          'holding-1',
          expect.objectContaining({ quantity: 10, price: 30 }),
        )
        expect(holdingsApi.getStockHolding).toHaveBeenCalledTimes(2)
        expect(wrapper.emitted('positionAdded')).toHaveLength(1)
        expect(wrapper.find('.modal-card').exists()).toBe(false)
      })

      it('records a crypto purchase through the crypto endpoint', async () => {
        vi.mocked(holdingsApi.addCryptoLot).mockResolvedValue(newLot)
        const wrapper = await mountLoadedDrawer(cryptoRow, cryptoDetail())

        await clickBuy(wrapper)
        const [quantityInput, priceInput] = modalNumberInputs(wrapper)
        await quantityInput.setValue('0.1')
        await priceInput.setValue('190000')
        await modalButton(wrapper, 'Registrar compra').trigger('click')
        await flushPromises()

        expect(holdingsApi.addCryptoLot).toHaveBeenCalledWith(
          'wallet-3',
          'holding-3',
          expect.objectContaining({ quantity: 0.1, price: 190000 }),
        )
        expect(holdingsApi.getCryptoHolding).toHaveBeenCalledTimes(2)
      })

      it('records a fund contribution', async () => {
        vi.mocked(holdingsApi.addContribution).mockResolvedValue(newContribution)
        const wrapper = await mountLoadedDrawer(fundRow, fundDetail())

        await clickBuy(wrapper)
        expect(wrapper.find('.modal-card-title').text()).toBe('Registrar aporte')
        await modalNumberInputs(wrapper)[0].setValue('500')
        await modalButton(wrapper, 'Registrar aporte').trigger('click')
        await flushPromises()

        expect(holdingsApi.addContribution).toHaveBeenCalledWith(
          'wallet-2',
          'holding-2',
          expect.objectContaining({ amount: 500 }),
        )
        expect(holdingsApi.getFundHolding).toHaveBeenCalledTimes(2)
        expect(wrapper.emitted('positionAdded')).toHaveLength(1)
      })

      it('closes the modal without recording anything when cancelled', async () => {
        const wrapper = await mountLoadedDrawer(stockRow, stockDetailWithoutWithdrawals())

        await clickBuy(wrapper)
        await modalButton(wrapper, 'Cancelar').trigger('click')
        await flushPromises()

        expect(wrapper.find('.modal-card').exists()).toBe(false)
        expect(holdingsApi.addStockLot).not.toHaveBeenCalled()
        expect(wrapper.emitted('positionAdded')).toBeUndefined()
        expect(wrapper.emitted('close')).toBeUndefined()
      })
    })

    describe('update price', () => {
      it('pre-fills the current price of a stock and saves the new one', async () => {
        vi.mocked(holdingsApi.updateStockHolding).mockResolvedValue(stockDetailWithoutWithdrawals())
        const wrapper = await mountLoadedDrawer(stockRow, stockDetailWithoutWithdrawals())

        await chooseAction('Atualizar preço')
        const priceInput = modalNumberInputs(wrapper)[0]
        expect((priceInput.element as HTMLInputElement).value).toBe('34.80')
        await priceInput.setValue('40')
        await modalButton(wrapper, 'Salvar').trigger('click')
        await flushPromises()

        expect(holdingsApi.updateStockHolding).toHaveBeenCalledWith('wallet-1', 'holding-1', {
          currentPrice: 40,
        })
        expect(holdingsApi.getStockHolding).toHaveBeenCalledTimes(2)
        expect(wrapper.emitted('positionAdded')).toHaveLength(1)
      })

      it('pre-fills the current price of a crypto and saves the new one', async () => {
        vi.mocked(holdingsApi.updateCryptoHolding).mockResolvedValue(cryptoDetail())
        const wrapper = await mountLoadedDrawer(cryptoRow, cryptoDetail())

        await chooseAction('Atualizar preço')
        const priceInput = modalNumberInputs(wrapper)[0]
        expect((priceInput.element as HTMLInputElement).value).toBe('180000.00')
        await priceInput.setValue('185000')
        await modalButton(wrapper, 'Salvar').trigger('click')
        await flushPromises()

        expect(holdingsApi.updateCryptoHolding).toHaveBeenCalledWith('wallet-3', 'holding-3', {
          currentPrice: 185000,
        })
      })

      it('pre-fills the current value of a fund and saves the new one', async () => {
        vi.mocked(holdingsApi.updateFundHolding).mockResolvedValue(fundDetail())
        const wrapper = await mountLoadedDrawer(fundRow, fundDetail())

        await chooseAction('Atualizar valor atual')
        const valueInput = modalNumberInputs(wrapper)[0]
        expect((valueInput.element as HTMLInputElement).value).toBe('3000.00')
        await valueInput.setValue('3300')
        await modalButton(wrapper, 'Salvar').trigger('click')
        await flushPromises()

        expect(holdingsApi.updateFundHolding).toHaveBeenCalledWith('wallet-2', 'holding-2', {
          currentValue: 3300,
        })
        expect(holdingsApi.getFundHolding).toHaveBeenCalledTimes(2)
      })

      it('pre-fills the stored rates of a fund in the update modal', async () => {
        const wrapper = await mountLoadedDrawer(
          fundRow,
          fundDetail({ administrationFeeRate: 1.5, performanceFeeRate: 20 }),
        )

        await chooseAction('Atualizar valor atual')
        const [, administrationInput, performanceInput] = modalNumberInputs(wrapper)

        expect(document.body.textContent).toContain('Atualizar fundo')
        expect((administrationInput.element as HTMLInputElement).value).toBe('1.5')
        expect((performanceInput.element as HTMLInputElement).value).toBe('20')
      })

      it('offers no fee rate fields in the update modal of a stock', async () => {
        const wrapper = await mountLoadedDrawer(stockRow, stockDetailWithoutWithdrawals())

        await chooseAction('Atualizar preço')

        expect(modalNumberInputs(wrapper)).toHaveLength(1)
        expect(document.querySelector('.modal-card')?.textContent).not.toContain(
          'Taxa de administração',
        )
      })
    })

    describe('withdraw', () => {
      it('records a partial withdrawal, reloads the detail and keeps the drawer', async () => {
        vi.mocked(resultsApi.withdrawFromStockHolding).mockResolvedValue(undefined)
        const wrapper = await mountLoadedDrawer(stockRow, stockDetailWithoutWithdrawals())

        await chooseAction(withdrawLabel(stockRow))
        expect(wrapper.find('.modal-card-title').text()).toBe('Resgatar')
        const [quantityInput, priceInput] = modalNumberInputs(wrapper)
        await quantityInput.setValue('50')
        await priceInput.setValue('40')
        await modalButton(wrapper, 'Resgatar').trigger('click')
        await flushPromises()

        expect(resultsApi.withdrawFromStockHolding).toHaveBeenCalledWith(
          'wallet-1',
          'holding-1',
          expect.objectContaining({ quantity: 50, unitPrice: 40 }),
        )
        expect(holdingsApi.getStockHolding).toHaveBeenCalledTimes(2)
        expect(wrapper.emitted('positionAdded')).toHaveLength(1)
        expect(wrapper.emitted('deleted')).toBeUndefined()
      })

      it('closes through the deleted event, without reloading, when the whole position is withdrawn', async () => {
        vi.mocked(resultsApi.withdrawFromStockHolding).mockResolvedValue(undefined)
        const wrapper = await mountLoadedDrawer(stockRow, stockDetailWithoutWithdrawals())

        await chooseAction(withdrawLabel(stockRow))
        const [quantityInput, priceInput] = modalNumberInputs(wrapper)
        await quantityInput.setValue('200')
        await priceInput.setValue('40')
        await modalButton(wrapper, 'Resgatar').trigger('click')
        await flushPromises()

        expect(wrapper.emitted('deleted')).toHaveLength(1)
        expect(wrapper.emitted('positionAdded')).toBeUndefined()
        expect(holdingsApi.getStockHolding).toHaveBeenCalledTimes(1)
      })

      it('treats a fund withdrawal of the whole current value as a full exit', async () => {
        vi.mocked(resultsApi.withdrawFromFundHolding).mockResolvedValue(undefined)
        const wrapper = await mountLoadedDrawer(fundRow, fundDetail())

        await chooseAction(withdrawLabel(fundRow))
        await modalNumberInputs(wrapper)[0].setValue('3000')
        await modalButton(wrapper, 'Resgatar').trigger('click')
        await flushPromises()

        expect(resultsApi.withdrawFromFundHolding).toHaveBeenCalledWith(
          'wallet-2',
          'holding-2',
          expect.objectContaining({ amount: 3000 }),
        )
        expect(wrapper.emitted('deleted')).toHaveLength(1)
      })

      it('withdraws from a crypto holding through the crypto endpoint', async () => {
        vi.mocked(resultsApi.withdrawFromCryptoHolding).mockResolvedValue(undefined)
        const wrapper = await mountLoadedDrawer(cryptoRow, cryptoDetail())

        await chooseAction(withdrawLabel(cryptoRow))
        const [quantityInput, priceInput] = modalNumberInputs(wrapper)
        await quantityInput.setValue('0.1')
        await priceInput.setValue('190000')
        await modalButton(wrapper, 'Resgatar').trigger('click')
        await flushPromises()

        expect(resultsApi.withdrawFromCryptoHolding).toHaveBeenCalledWith(
          'wallet-3',
          'holding-3',
          expect.objectContaining({ quantity: 0.1, unitPrice: 190000 }),
        )
        expect(wrapper.emitted('positionAdded')).toHaveLength(1)
      })
    })

    describe('reinvest and move', () => {
      it('opens Reinvestir with this holding as the source and reports the relocation', async () => {
        const wrapper = await mountLoadedDrawer(stockRow, stockDetailWithWithdrawals())
        const reinvestmentsStore = useReinvestmentsStore()
        vi.mocked(reinvestmentsStore.reinvest).mockResolvedValue(undefined)
        vi.mocked(holdingsApi.findAll).mockResolvedValue({
          content: [stockRow, { ...stockRow, id: 'holding-9', ticker: 'VALE3' }],
          page: { size: 500, number: 0, totalElements: 2, totalPages: 1 },
        })

        await chooseAction('Reinvestir')

        expect((control(wrapper, 'reinvest-source').element as HTMLSelectElement).value).toBe(
          'holding-1',
        )

        await control(wrapper, 'reinvest-destination').setValue('holding-9')
        await control(wrapper, 'reinvest-quantity').setValue('10')
        await wrapper.find('[data-testid="reinvest-submit"]').trigger('click')
        await flushPromises()

        expect(reinvestmentsStore.reinvest).toHaveBeenCalledTimes(1)
        expect(wrapper.emitted('relocated')).toHaveLength(1)
      })

      it('opens Mover with this holding preselected in its own wallet', async () => {
        const wrapper = await mountLoadedDrawer(stockRow, stockDetailWithWithdrawals())
        const wallet: WalletResponse = {
          id: 'wallet-1',
          name: 'Carteira B3',
          kind: 'STOCKS',
          currency: 'BRL',
          holdingCount: 1,
          totalInvested: 5652,
          currentValue: 6960,
          gain: 1308,
          gainPct: 23.1,
          cash: 0,
          cashReconciled: true,
          createdAt: '2026-01-01T00:00:00Z',
        }
        const walletsStore = useWalletsStore()
        walletsStore.wallets = [wallet]
        walletsStore.walletById = (id: string) => (id === wallet.id ? wallet : undefined)

        await chooseAction('Mover para outra carteira')

        expect(wrapper.text()).toContain('Mover investimentos')
        expect(wrapper.find('[data-testid="move-origin"]').exists()).toBe(false)
        expect(holdingsApi.findAll).toHaveBeenCalledWith({ walletId: 'wallet-1', size: 500 })
        expect(wrapper.find('.move-item.is-selected').exists()).toBe(true)
      })
    })

    describe('remove', () => {
      it.each(kindCases)(
        'removes the $label holding on confirm and reports it as deleted',
        async ({ row, detail, deleteHolding }) => {
          deleteHolding.mockResolvedValue(undefined)
          const wrapper = await mountLoadedDrawer(row, detail())

          await chooseAction('Remover investimento')
          await flushPromises()
          expect(document.body.textContent).toContain('Remover investimento')
          await confirmDialog('Remover')

          expect(deleteHolding).toHaveBeenCalledWith(row.walletId, row.id)
          expect(wrapper.emitted('deleted')).toHaveLength(1)
        },
      )

      it('removes nothing when the dialog is cancelled', async () => {
        const wrapper = await mountLoadedDrawer(stockRow, stockDetailWithoutWithdrawals())

        await chooseAction('Remover investimento')
        await confirmDialog('Cancelar')

        expect(holdingsApi.deleteStockHolding).not.toHaveBeenCalled()
        expect(wrapper.emitted('deleted')).toBeUndefined()
      })
    })
  })

  describe('loading overlay', () => {
    it('covers the drawer while the detail is being fetched and leaves once it arrives', async () => {
      const [{ row, detail, getHolding }] = kindCases
      let resolveDetail!: (loaded: HoldingDetail) => void
      getHolding.mockReturnValue(
        new Promise<HoldingDetail>((resolve) => {
          resolveDetail = resolve
        }),
      )

      const wrapper = mountDrawer(row)
      await flushPromises()
      expect(wrapper.findAll('.loading-overlay')).toHaveLength(1)

      resolveDetail(detail())
      await flushPromises()
      expect(wrapper.find('.loading-overlay').exists()).toBe(false)
    })

    it('leaves the overlay once a failed fetch settles', async () => {
      const [{ row, getHolding }] = kindCases
      getHolding.mockRejectedValue(new Error('boom'))

      const wrapper = mountDrawer(row)
      await flushPromises()

      expect(wrapper.find('.loading-overlay').exists()).toBe(false)
    })
  })
})
