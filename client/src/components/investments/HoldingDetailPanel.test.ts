import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import HoldingDetailPanel from './HoldingDetailPanel.vue'
import { holdingsApi } from '@/api/holdings'
import { resultsApi } from '@/api/results'
import { useAuthStore } from '@/stores/auth'
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
    deleteStockHolding: vi.fn(),
    deleteCryptoHolding: vi.fn(),
    deleteFundHolding: vi.fn(),
    deleteStockLot: vi.fn(),
    deleteCryptoLot: vi.fn(),
    deleteFundContribution: vi.fn(),
    updateStockLotDate: vi.fn(),
    updateCryptoLotDate: vi.fn(),
    updateFundContributionDate: vi.fn(),
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
  walletId: 'wallet-1',
  walletName: 'Carteira B3',
  walletCurrency: 'BRL',
  quantity: 200,
  costBasis: 5652,
  currentPrice: 34.8,
  currentValue: 6960,
  gain: 1308,
  gainPct: 23.1,
}

const cryptoRow: HoldingRow = {
  id: 'holding-3',
  kind: 'CRYPTO',
  name: 'Bitcoin',
  ticker: 'BTC',
  typeLabel: null,
  walletId: 'wallet-3',
  walletName: 'Carteira Cripto',
  walletCurrency: 'BRL',
  quantity: 0.5,
  costBasis: 100000,
  currentPrice: 180000,
  currentValue: 90000,
  gain: -10000,
  gainPct: -10,
}

const fundRow: HoldingRow = {
  id: 'holding-2',
  kind: 'FUNDS',
  name: 'Tesouro IPCA+',
  ticker: null,
  typeLabel: 'Renda Fixa',
  walletId: 'wallet-2',
  walletName: 'Carteira Fundos',
  walletCurrency: 'BRL',
  quantity: null,
  costBasis: 3000,
  currentPrice: null,
  currentValue: 3000,
  gain: 0,
  gainPct: 0,
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
    stockTypeId: 'type-1',
    ticker: 'PETR4',
    name: 'Petróleo Brasileiro',
    currentPrice: 34.8,
    lots: [
      { id: 'lot-1', lotDate: '2026-01-12', quantity: 300, price: 25.1 },
      { id: 'lot-2', lotDate: '2026-03-03', quantity: 200, price: 33 },
    ],
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
    stockTypeId: 'type-1',
    ticker: 'PETR4',
    name: 'Petróleo Brasileiro',
    currentPrice: 34.8,
    lots: [{ id: 'lot-1', lotDate: '2026-01-12', quantity: 300, price: 25.1 }],
    withdrawals: [],
  }
}

function cryptoDetail(): CryptoHoldingDetail {
  return {
    id: 'holding-3',
    walletId: 'wallet-3',
    ticker: 'BTC',
    name: 'Bitcoin',
    currentPrice: 180000,
    lots: [{ id: 'lot-9', lotDate: '2026-02-01', quantity: 0.5, price: 200000 }],
    withdrawals: [],
  }
}

function fundDetail(overrides: Partial<FundHoldingDetail> = {}): FundHoldingDetail {
  return {
    id: 'holding-2',
    walletId: 'wallet-2',
    fundTypeId: 'type-2',
    name: 'Tesouro IPCA+',
    currentValue: 3000,
    administrationFeeRate: null,
    performanceFeeRate: null,
    contributions: [{ id: 'c-1', contributionDate: '2026-01-10', amount: 4000 }],
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
  deleteHolding: ApiMock
  deleteEntry: ApiMock
  deleteWithdrawal: ApiMock
  updateEntryDate: ApiMock
  entryId: string
  dateField: 'lotDate' | 'contributionDate'
}

const kindCases: KindCase[] = [
  {
    label: 'stock',
    row: stockRow,
    detail: stockDetailWithoutWithdrawals,
    getHolding: vi.mocked(holdingsApi.getStockHolding),
    deleteHolding: vi.mocked(holdingsApi.deleteStockHolding),
    deleteEntry: vi.mocked(holdingsApi.deleteStockLot),
    deleteWithdrawal: vi.mocked(resultsApi.deleteStockWithdrawal),
    updateEntryDate: vi.mocked(holdingsApi.updateStockLotDate),
    entryId: 'lot-1',
    dateField: 'lotDate',
  },
  {
    label: 'crypto',
    row: cryptoRow,
    detail: cryptoDetail,
    getHolding: vi.mocked(holdingsApi.getCryptoHolding),
    deleteHolding: vi.mocked(holdingsApi.deleteCryptoHolding),
    deleteEntry: vi.mocked(holdingsApi.deleteCryptoLot),
    deleteWithdrawal: vi.mocked(resultsApi.deleteCryptoWithdrawal),
    updateEntryDate: vi.mocked(holdingsApi.updateCryptoLotDate),
    entryId: 'lot-9',
    dateField: 'lotDate',
  },
  {
    label: 'fund',
    row: fundRow,
    detail: fundDetail,
    getHolding: vi.mocked(holdingsApi.getFundHolding),
    deleteHolding: vi.mocked(holdingsApi.deleteFundHolding),
    deleteEntry: vi.mocked(holdingsApi.deleteFundContribution),
    deleteWithdrawal: vi.mocked(resultsApi.deleteFundWithdrawal),
    updateEntryDate: vi.mocked(holdingsApi.updateFundContributionDate),
    entryId: 'c-1',
    dateField: 'contributionDate',
  },
]

let activeWrapper: VueWrapper | undefined

function mountPanel(row: HoldingRow, options: { isAdmin?: boolean } = {}) {
  const pinia = createTestingPinia()
  const wrapper = mount(HoldingDetailPanel, {
    props: { row },
    global: { plugins: [pinia], config: { errorHandler: () => undefined } },
    attachTo: document.body,
  })
  activeWrapper = wrapper
  vi.mocked(useCurrencyStore().convert).mockImplementation((amount: number) => amount)
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

async function mountLoadedPanel(
  row: HoldingRow,
  detail: HoldingDetail,
  options: { isAdmin?: boolean } = {},
) {
  for (const kindCase of kindCases) {
    if (kindCase.row.kind === row.kind) kindCase.getHolding.mockResolvedValue(detail)
  }
  const wrapper = mountPanel(row, options)
  await flushPromises()
  return wrapper
}

function ledgerRows(wrapper: VueWrapper) {
  return wrapper.findAll('tbody tr')
}

function headerLabels(wrapper: VueWrapper) {
  return wrapper.findAll('thead th').map((cell) => cell.text())
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

describe('HoldingDetailPanel', () => {
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

  describe('lazy detail fetch', () => {
    it.each(kindCases)(
      'fetches the $label holding once from its own endpoint using the row wallet and id',
      async ({ row, detail, getHolding }) => {
        getHolding.mockResolvedValue(detail())

        mountPanel(row)
        await flushPromises()

        expect(getHolding).toHaveBeenCalledTimes(1)
        expect(getHolding).toHaveBeenCalledWith(row.walletId, row.id)
        for (const other of kindCases.filter((kindCase) => kindCase.row.kind !== row.kind)) {
          expect(other.getHolding).not.toHaveBeenCalled()
        }
      },
    )

    it('renders neither the ledger nor the actions while the fetch is in flight', async () => {
      vi.mocked(holdingsApi.getStockHolding).mockReturnValue(new Promise(() => undefined))

      const wrapper = mountPanel(stockRow)
      await flushPromises()

      expect(wrapper.find('table').exists()).toBe(false)
      expect(wrapper.find('[data-testid="holding-actions"]').exists()).toBe(false)
    })

    it('renders the ledger and the actions once the fetch resolves', async () => {
      const wrapper = await mountLoadedPanel(stockRow, stockDetailWithoutWithdrawals())

      expect(wrapper.find('table.sub-table').exists()).toBe(true)
      expect(wrapper.find('[data-testid="holding-actions"]').exists()).toBe(true)
    })

    it('renders neither the ledger nor the actions when the fetch fails', async () => {
      vi.mocked(holdingsApi.getStockHolding).mockRejectedValue(new Error('500'))

      const wrapper = mountPanel(stockRow)
      await flushPromises()

      expect(wrapper.find('table').exists()).toBe(false)
      expect(wrapper.find('[data-testid="holding-actions"]').exists()).toBe(false)
      expect(wrapper.text()).toBe('')
    })
  })

  describe('ledger', () => {
    it('renders the ledger (Tipo/Custos/Resultado/Saldo included) even for a holding with no withdrawals', async () => {
      const wrapper = await mountLoadedPanel(stockRow, stockDetailWithoutWithdrawals())

      expect(wrapper.text()).toContain('Tipo')
      expect(wrapper.text()).toContain('Saldo')
      expect(wrapper.text()).toContain('Valor')
      expect(wrapper.text()).not.toContain('Venda')

      const rows = ledgerRows(wrapper)
      expect(rows).toHaveLength(1)
      expect(rows[0].text()).toContain('Compra')
      expect(rows[0].text()).toContain('+300')
    })

    it('counts the movements in the header', async () => {
      const wrapper = await mountLoadedPanel(stockRow, stockDetailWithWithdrawals())

      expect(wrapper.find('.ledger-title').text()).toBe('Movimentações')
      expect(wrapper.find('.ledger-count').text()).toBe('4')
    })

    it('merges purchases and withdrawals into one ledger with signed quantities and a running balance', async () => {
      const wrapper = await mountLoadedPanel(stockRow, stockDetailWithWithdrawals())

      const rows = ledgerRows(wrapper)
      expect(rows).toHaveLength(4)

      expect(rows[0].text()).toContain('Compra')
      expect(rows[0].text()).toContain('+300')
      expect(rows[2].text()).toContain('Venda')
      expect(rows[2].text()).toContain('−150')

      const lastRowCells = rows[3].findAll('td')
      expect(lastRowCells[lastRowCells.length - 2].text()).toBe('200')

      expect(rows[0].find('td.c-act button').exists()).toBe(true)
      expect(rows[2].find('td.c-act button').exists()).toBe(true)
    })

    it('shows the unit price, the amount and a dash for the costs of a purchase', async () => {
      const wrapper = await mountLoadedPanel(stockRow, stockDetailWithoutWithdrawals())

      const cells = ledgerRows(wrapper)[0].findAll('td')
      expect(cells[3].text()).toBe('R$ 25,10')
      expect(cells[4].text()).toBe('—')
      expect(cells[5].text()).toBe('R$ 7.530,00')
      expect(cells[6].text()).toBe('—')
    })

    it('breaks the costs of a withdrawal into fees and taxes and shows its net amount and profit', async () => {
      const wrapper = await mountLoadedPanel(stockRow, stockDetailWithWithdrawals())

      const cells = ledgerRows(wrapper)[2].findAll('td')
      expect(cells[3].text()).toBe('R$ 30,50')
      expect(cells[4].text()).toContain('R$ 130,00')
      expect(cells[4].text()).toContain('taxa R$ 10,00 + imp. R$ 120,00')
      expect(cells[5].text()).toBe('R$ 4.445,00')
      expect(cells[6].text()).toBe('+R$ 206,00')
    })

    it('renders a crypto holding with the same columns as a stock', async () => {
      const wrapper = await mountLoadedPanel(cryptoRow, cryptoDetail())

      expect(headerLabels(wrapper)).toEqual([
        'Tipo',
        'Data da compra',
        'Qtd.',
        'Preço unit.',
        'Custos',
        'Valor',
        'Resultado',
        'Saldo',
        '',
      ])
      const row = ledgerRows(wrapper)[0]
      expect(row.text()).toContain('Compra')
      expect(row.text()).toContain('+0,5')
      expect(row.text()).toContain('R$ 200.000,00')
    })

    it('skips the quantity/price/balance columns for a fund and tags its rows as Aporte', async () => {
      const wrapper = await mountLoadedPanel(fundRow, fundDetail())

      expect(headerLabels(wrapper)).toEqual([
        'Tipo',
        'Data do aporte',
        'Custos',
        'Valor',
        'Resultado',
        '',
      ])
      expect(wrapper.text()).toContain('Aporte')
      expect(wrapper.text()).toContain('Resgate')
      expect(wrapper.text()).not.toContain('Qtd.')
      expect(wrapper.text()).not.toContain('Saldo')

      const rows = ledgerRows(wrapper)
      expect(rows[0].text()).toContain('R$ 4.000,00')
      expect(rows[1].text()).toContain('−R$ 15,00')
    })

    it('shows a loss as a negative result', async () => {
      const detail = stockDetailWithWithdrawals()
      detail.withdrawals[0] = withdrawalOf({ profit: -80 })

      const wrapper = await mountLoadedPanel(stockRow, detail)

      expect(ledgerRows(wrapper)[2].text()).toContain('−R$ 80,00')
    })

    it('tags a reinvestment row as Reinvestimento and offers no undo for it', async () => {
      const detail = stockDetailWithWithdrawals()
      detail.withdrawals[1] = { ...detail.withdrawals[1], resultType: 'REINVESTMENT' }

      const wrapper = await mountLoadedPanel(stockRow, detail)

      const reinvestmentRow = ledgerRows(wrapper)[3]
      expect(reinvestmentRow.text()).toContain('Reinvestimento')
      expect(reinvestmentRow.find('td.c-act button').exists()).toBe(false)
      expect(ledgerRows(wrapper)[2].find('td.c-act button').exists()).toBe(true)
    })

    it('shows a withdrawal without a quantity as a dash', async () => {
      const detail = stockDetailWithWithdrawals()
      detail.withdrawals[0] = withdrawalOf({ quantity: null })

      const wrapper = await mountLoadedPanel(stockRow, detail)

      const cells = ledgerRows(wrapper)[2].findAll('td')
      expect(cells[2].text()).toBe('—')
      expect(cells[3].find('.gl-empty').exists()).toBe(true)
    })

    it('shows the amounts in the display currency', async () => {
      vi.mocked(holdingsApi.getStockHolding).mockResolvedValue(stockDetailWithoutWithdrawals())
      const wrapper = mountPanel({ ...stockRow, walletCurrency: 'USD' })
      vi.mocked(useCurrencyStore().convert).mockImplementation((amount) => amount * 5)
      await flushPromises()

      expect(useCurrencyStore().convert).toHaveBeenCalledWith(25.1, 'USD')
      expect(ledgerRows(wrapper)[0].findAll('td')[3].text()).toBe('R$ 125,50')
    })

    it('offers no delete buttons to a non-admin', async () => {
      const wrapper = await mountLoadedPanel(stockRow, stockDetailWithWithdrawals(), {
        isAdmin: false,
      })

      expect(wrapper.findAll('td.c-act button')).toHaveLength(0)
    })
  })

  describe('fund fee rates', () => {
    function feeRateText(wrapper: VueWrapper, testId: string) {
      return wrapper.find(`[data-testid="${testId}"]`).text()
    }

    it.each(kindCases)(
      'shows the fee rates block only for a fund, not for a $label holding',
      async ({ row, detail }) => {
        const wrapper = await mountLoadedPanel(row, detail())

        expect(wrapper.find('[data-testid="fund-fees"]').exists()).toBe(row.kind === 'FUNDS')
      },
    )

    it('labels both rates with their units', async () => {
      const wrapper = await mountLoadedPanel(fundRow, fundDetail())

      const block = wrapper.find('[data-testid="fund-fees"]')
      expect(block.text()).toContain('Taxa de administração (% a.a.)')
      expect(block.text()).toContain('Taxa de performance (%)')
    })

    it('shows an em dash for each rate that is null', async () => {
      const wrapper = await mountLoadedPanel(fundRow, fundDetail())

      expect(feeRateText(wrapper, 'administration-fee-rate')).toBe('—')
      expect(feeRateText(wrapper, 'performance-fee-rate')).toBe('—')
    })

    it('shows both rates as percentages', async () => {
      const wrapper = await mountLoadedPanel(
        fundRow,
        fundDetail({ administrationFeeRate: 1.5, performanceFeeRate: 20 }),
      )

      expect(feeRateText(wrapper, 'administration-fee-rate')).toBe('1,50%')
      expect(feeRateText(wrapper, 'performance-fee-rate')).toBe('20,00%')
    })

    it('shows a rate of zero as 0,00% rather than a dash', async () => {
      const wrapper = await mountLoadedPanel(
        fundRow,
        fundDetail({ administrationFeeRate: 0, performanceFeeRate: null }),
      )

      expect(feeRateText(wrapper, 'administration-fee-rate')).toBe('0,00%')
      expect(feeRateText(wrapper, 'performance-fee-rate')).toBe('—')
    })

    it('shows only one rate when the other is null', async () => {
      const wrapper = await mountLoadedPanel(
        fundRow,
        fundDetail({ administrationFeeRate: null, performanceFeeRate: 15.5 }),
      )

      expect(feeRateText(wrapper, 'administration-fee-rate')).toBe('—')
      expect(feeRateText(wrapper, 'performance-fee-rate')).toBe('15,50%')
    })

    it('reflects the rates saved from the update modal after the detail reloads', async () => {
      vi.mocked(holdingsApi.updateFundHolding).mockResolvedValue(fundDetail())
      const wrapper = await mountLoadedPanel(fundRow, fundDetail())
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
      expect(feeRateText(wrapper, 'administration-fee-rate')).toBe('2,00%')
      expect(feeRateText(wrapper, 'performance-fee-rate')).toBe('10,00%')
    })
  })

  describe('purchase removal', () => {
    it.each(kindCases)(
      'removes a $label entry on confirm, reloads the detail and reports the change',
      async ({ row, detail, getHolding, deleteEntry, entryId }) => {
        deleteEntry.mockResolvedValue(undefined)
        const wrapper = await mountLoadedPanel(row, detail())

        await ledgerRows(wrapper)[0].find('td.c-act button').trigger('click')
        await confirmDialog('Remover')

        expect(deleteEntry).toHaveBeenCalledWith(row.walletId, row.id, entryId)
        expect(getHolding).toHaveBeenCalledTimes(2)
        expect(wrapper.emitted('positionAdded')).toHaveLength(1)
      },
    )

    it('words the stock dialog and toast as a purchase', async () => {
      vi.mocked(holdingsApi.deleteStockLot).mockResolvedValue(undefined)
      const wrapper = await mountLoadedPanel(stockRow, stockDetailWithoutWithdrawals())

      await ledgerRows(wrapper)[0].find('td.c-act button').trigger('click')
      await flushPromises()
      expect(document.body.textContent).toContain('Remover compra')
      await confirmDialog('Remover')

      expect(document.body.textContent).toContain('Compra removida.')
    })

    it('words the fund dialog and toast as a contribution', async () => {
      vi.mocked(holdingsApi.deleteFundContribution).mockResolvedValue(undefined)
      const wrapper = await mountLoadedPanel(fundRow, fundDetail())

      await ledgerRows(wrapper)[0].find('td.c-act button').trigger('click')
      await flushPromises()
      expect(document.body.textContent).toContain('Remover aporte')
      await confirmDialog('Remover')

      expect(document.body.textContent).toContain('Aporte removido.')
    })

    it('keeps everything as it was when the dialog is cancelled', async () => {
      const wrapper = await mountLoadedPanel(stockRow, stockDetailWithoutWithdrawals())

      await ledgerRows(wrapper)[0].find('td.c-act button').trigger('click')
      await confirmDialog('Cancelar')

      expect(holdingsApi.deleteStockLot).not.toHaveBeenCalled()
      expect(holdingsApi.getStockHolding).toHaveBeenCalledTimes(1)
      expect(wrapper.emitted('positionAdded')).toBeUndefined()
    })
  })

  describe('withdrawal undo', () => {
    it('undoes the most recent withdrawal on confirm', async () => {
      vi.mocked(resultsApi.deleteStockWithdrawal).mockResolvedValue(undefined)
      const wrapper = await mountLoadedPanel(stockRow, stockDetailWithWithdrawals())

      await ledgerRows(wrapper)[3].find('td.c-act button').trigger('click')
      await confirmDialog('Desfazer')

      expect(resultsApi.deleteStockWithdrawal).toHaveBeenCalledWith('wallet-1', 'holding-1', 'w-2')
      expect(holdingsApi.getStockHolding).toHaveBeenCalledTimes(2)
      expect(wrapper.emitted('positionAdded')).toHaveLength(1)
    })

    it('words the stock dialog and toast as a sale', async () => {
      vi.mocked(resultsApi.deleteStockWithdrawal).mockResolvedValue(undefined)
      const wrapper = await mountLoadedPanel(stockRow, stockDetailWithWithdrawals())

      await ledgerRows(wrapper)[3].find('td.c-act button').trigger('click')
      await flushPromises()
      expect(document.body.textContent).toContain('Desfazer venda')
      await confirmDialog('Desfazer')

      expect(document.body.textContent).toContain('Venda desfeita.')
    })

    it('undoes a crypto sale through the crypto endpoint', async () => {
      vi.mocked(resultsApi.deleteCryptoWithdrawal).mockResolvedValue(undefined)
      const detail = cryptoDetail()
      detail.withdrawals = [withdrawalOf({ id: 'cw-1', resultDate: '2026-04-01', quantity: 0.1 })]
      const wrapper = await mountLoadedPanel(cryptoRow, detail)

      await ledgerRows(wrapper)[1].find('td.c-act button').trigger('click')
      await confirmDialog('Desfazer')

      expect(resultsApi.deleteCryptoWithdrawal).toHaveBeenCalledWith('wallet-3', 'holding-3', 'cw-1')
      expect(holdingsApi.getCryptoHolding).toHaveBeenCalledTimes(2)
    })

    it('words a fund withdrawal as a redemption and undoes it through the fund endpoint', async () => {
      vi.mocked(resultsApi.deleteFundWithdrawal).mockResolvedValue(undefined)
      const wrapper = await mountLoadedPanel(fundRow, fundDetail())

      await ledgerRows(wrapper)[1].find('td.c-act button').trigger('click')
      await flushPromises()
      expect(document.body.textContent).toContain('Desfazer resgate')
      await confirmDialog('Desfazer')

      expect(resultsApi.deleteFundWithdrawal).toHaveBeenCalledWith('wallet-2', 'holding-2', 'fw-1')
      expect(document.body.textContent).toContain('Resgate desfeito.')
    })

    it('lets the server reject undoing a withdrawal that is not the most recent, without reloading', async () => {
      vi.mocked(resultsApi.deleteStockWithdrawal).mockRejectedValue(new Error('409 Conflict'))
      const wrapper = await mountLoadedPanel(stockRow, stockDetailWithWithdrawals())

      await ledgerRows(wrapper)[2].find('td.c-act button').trigger('click')
      await confirmDialog('Desfazer')

      expect(resultsApi.deleteStockWithdrawal).toHaveBeenCalledWith('wallet-1', 'holding-1', 'w-1')
      expect(holdingsApi.getStockHolding).toHaveBeenCalledTimes(1)
      expect(wrapper.emitted('positionAdded')).toBeUndefined()
    })
  })

  describe('date editing', () => {
    const pickedDate = new Date(Date.UTC(2026, 1, 3, 12))

    it('shows each purchase date as a button and a withdrawal date as plain text', async () => {
      const wrapper = await mountLoadedPanel(stockRow, stockDetailWithWithdrawals())

      const rows = ledgerRows(wrapper)
      expect(rows[0].find('button.date-edit').text()).toBe('12 jan 2026')
      expect(rows[2].find('button.date-edit').exists()).toBe(false)
      expect(rows[2].findAll('td')[1].text()).toBe('10 mai 2026')
    })

    it('turns the date into a date picker when it is clicked', async () => {
      const wrapper = await mountLoadedPanel(stockRow, stockDetailWithoutWithdrawals())

      await wrapper.find('button.date-edit').trigger('click')

      expect(wrapper.find('button.date-edit').exists()).toBe(false)
      expect(wrapper.find('.datepicker').exists()).toBe(true)
    })

    it.each(kindCases)(
      'saves the picked date of a $label entry, reloads and reports the change',
      async ({ row, detail, getHolding, updateEntryDate, entryId, dateField }) => {
        updateEntryDate.mockResolvedValue(undefined)
        const wrapper = await mountLoadedPanel(row, detail())

        await wrapper.find('button.date-edit').trigger('click')
        wrapper.getComponent({ name: 'BDatepicker' }).vm.$emit('update:modelValue', pickedDate)
        await flushPromises()

        expect(updateEntryDate).toHaveBeenCalledWith(row.walletId, row.id, entryId, {
          [dateField]: '2026-02-03',
        })
        expect(getHolding).toHaveBeenCalledTimes(2)
        expect(wrapper.emitted('positionAdded')).toHaveLength(1)
        expect(wrapper.find('.datepicker').exists()).toBe(false)
        expect(document.body.textContent).toContain('Data atualizada.')
      },
    )

    it('ignores the picker being cleared', async () => {
      const wrapper = await mountLoadedPanel(stockRow, stockDetailWithoutWithdrawals())

      await wrapper.find('button.date-edit').trigger('click')
      wrapper.getComponent({ name: 'BDatepicker' }).vm.$emit('update:modelValue', null)
      await flushPromises()

      expect(holdingsApi.updateStockLotDate).not.toHaveBeenCalled()
      expect(holdingsApi.getStockHolding).toHaveBeenCalledTimes(1)
    })
  })

  describe('Ações dropdown', () => {
    it('replaces the separate buttons with one dropdown listing every holding action in order', async () => {
      const wrapper = await mountLoadedPanel(stockRow, stockDetailWithWithdrawals())

      expect(wrapper.find('[data-testid="holding-actions"] button').text()).toBe('Ações')
      expect(actionLabels()).toEqual([
        'Registrar nova compra',
        'Atualizar preço',
        'Resgatar',
        'Reinvestir',
        'Mover',
        'Remover',
      ])
      expect(wrapper.find('.ledger-actions').findAll(':scope > button')).toHaveLength(0)
    })

    it('words the crypto actions as purchases and prices', async () => {
      await mountLoadedPanel(cryptoRow, cryptoDetail())

      expect(actionLabels().slice(0, 2)).toEqual(['Registrar nova compra', 'Atualizar preço'])
    })

    it('uses the fund wording and hides Remover from a non-admin', async () => {
      await mountLoadedPanel(fundRow, fundDetail(), { isAdmin: false })

      expect(actionLabels()).toEqual([
        'Registrar novo aporte',
        'Atualizar valor atual',
        'Resgatar',
        'Reinvestir',
        'Mover',
      ])
    })

    describe('register purchase', () => {
      it('records a stock purchase, reloads the detail and reports the change', async () => {
        vi.mocked(holdingsApi.addStockLot).mockResolvedValue(newLot)
        const wrapper = await mountLoadedPanel(stockRow, stockDetailWithoutWithdrawals())

        await chooseAction('Registrar nova compra')
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
        const wrapper = await mountLoadedPanel(cryptoRow, cryptoDetail())

        await chooseAction('Registrar nova compra')
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
        const wrapper = await mountLoadedPanel(fundRow, fundDetail())

        await chooseAction('Registrar novo aporte')
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
        const wrapper = await mountLoadedPanel(stockRow, stockDetailWithoutWithdrawals())

        await chooseAction('Registrar nova compra')
        await modalButton(wrapper, 'Cancelar').trigger('click')
        await flushPromises()

        expect(wrapper.find('.modal-card').exists()).toBe(false)
        expect(holdingsApi.addStockLot).not.toHaveBeenCalled()
        expect(wrapper.emitted('positionAdded')).toBeUndefined()
      })
    })

    describe('update price', () => {
      it('pre-fills the current price of a stock and saves the new one', async () => {
        vi.mocked(holdingsApi.updateStockHolding).mockResolvedValue(stockDetailWithoutWithdrawals())
        const wrapper = await mountLoadedPanel(stockRow, stockDetailWithoutWithdrawals())

        await chooseAction('Atualizar preço')
        const priceInput = modalNumberInputs(wrapper)[0]
        expect((priceInput.element as HTMLInputElement).value).toBe('34.8')
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
        const wrapper = await mountLoadedPanel(cryptoRow, cryptoDetail())

        await chooseAction('Atualizar preço')
        const priceInput = modalNumberInputs(wrapper)[0]
        expect((priceInput.element as HTMLInputElement).value).toBe('180000')
        await priceInput.setValue('185000')
        await modalButton(wrapper, 'Salvar').trigger('click')
        await flushPromises()

        expect(holdingsApi.updateCryptoHolding).toHaveBeenCalledWith('wallet-3', 'holding-3', {
          currentPrice: 185000,
        })
      })

      it('pre-fills the current value of a fund and saves the new one', async () => {
        vi.mocked(holdingsApi.updateFundHolding).mockResolvedValue(fundDetail())
        const wrapper = await mountLoadedPanel(fundRow, fundDetail())

        await chooseAction('Atualizar valor atual')
        const valueInput = modalNumberInputs(wrapper)[0]
        expect((valueInput.element as HTMLInputElement).value).toBe('3000')
        await valueInput.setValue('3300')
        await modalButton(wrapper, 'Salvar').trigger('click')
        await flushPromises()

        expect(holdingsApi.updateFundHolding).toHaveBeenCalledWith('wallet-2', 'holding-2', {
          currentValue: 3300,
        })
        expect(holdingsApi.getFundHolding).toHaveBeenCalledTimes(2)
      })

      it('pre-fills the stored rates of a fund in the update modal', async () => {
        const wrapper = await mountLoadedPanel(
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
        const wrapper = await mountLoadedPanel(stockRow, stockDetailWithoutWithdrawals())

        await chooseAction('Atualizar preço')

        expect(modalNumberInputs(wrapper)).toHaveLength(1)
        expect(document.body.textContent).not.toContain('Taxa de administração')
      })
    })

    describe('withdraw', () => {
      it('records a partial withdrawal, reloads the detail and keeps the panel', async () => {
        vi.mocked(resultsApi.withdrawFromStockHolding).mockResolvedValue(undefined)
        const wrapper = await mountLoadedPanel(stockRow, stockDetailWithoutWithdrawals())

        await chooseAction('Resgatar')
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

      it('collapses through the deleted event, without reloading, when the whole position is withdrawn', async () => {
        vi.mocked(resultsApi.withdrawFromStockHolding).mockResolvedValue(undefined)
        const wrapper = await mountLoadedPanel(stockRow, stockDetailWithoutWithdrawals())

        await chooseAction('Resgatar')
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
        const wrapper = await mountLoadedPanel(fundRow, fundDetail())

        await chooseAction('Resgatar')
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
        const wrapper = await mountLoadedPanel(cryptoRow, cryptoDetail())

        await chooseAction('Resgatar')
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
        const wrapper = await mountLoadedPanel(stockRow, stockDetailWithWithdrawals())
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
        const wrapper = await mountLoadedPanel(stockRow, stockDetailWithWithdrawals())
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
          createdAt: '2026-01-01T00:00:00Z',
        }
        const walletsStore = useWalletsStore()
        walletsStore.wallets = [wallet]
        walletsStore.walletById = (id: string) => (id === wallet.id ? wallet : undefined)

        await chooseAction('Mover')

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
          const wrapper = await mountLoadedPanel(row, detail())

          await chooseAction('Remover')
          await flushPromises()
          expect(document.body.textContent).toContain('Remover investimento')
          await confirmDialog('Remover')

          expect(deleteHolding).toHaveBeenCalledWith(row.walletId, row.id)
          expect(wrapper.emitted('deleted')).toHaveLength(1)
        },
      )

      it('removes nothing when the dialog is cancelled', async () => {
        const wrapper = await mountLoadedPanel(stockRow, stockDetailWithoutWithdrawals())

        await chooseAction('Remover')
        await confirmDialog('Cancelar')

        expect(holdingsApi.deleteStockHolding).not.toHaveBeenCalled()
        expect(wrapper.emitted('deleted')).toBeUndefined()
      })
    })
  })
})
