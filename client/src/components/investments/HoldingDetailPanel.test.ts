import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import HoldingDetailPanel from './HoldingDetailPanel.vue'
import { holdingsApi } from '@/api/holdings'
import { useCurrencyStore } from '@/stores/currency'
import type { FundHoldingDetail, HoldingRow, StockHoldingDetail } from '@/types'

vi.mock('@/api/holdings', () => ({
  holdingsApi: {
    getStockHolding: vi.fn(),
    getCryptoHolding: vi.fn(),
    getFundHolding: vi.fn(),
  },
}))

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
      {
        id: 'w-1',
        resultDate: '2026-05-10',
        quantity: 150,
        grossAmount: 4575,
        fees: 10,
        taxes: 120,
        costBasis: 4239,
        netAmount: 4445,
        profit: 206,
      },
      {
        id: 'w-2',
        resultDate: '2026-06-18',
        quantity: 150,
        grossAmount: 4815,
        fees: 10,
        taxes: 126,
        costBasis: 4239,
        netAmount: 4679,
        profit: 440,
      },
    ],
  }
}

function mountPanel(row: HoldingRow) {
  const pinia = createTestingPinia()
  const wrapper = mount(HoldingDetailPanel, {
    props: { row },
    global: { plugins: [pinia] },
  })
  const currencyStore = useCurrencyStore()
  vi.mocked(currencyStore.convert).mockImplementation((amount: number) => amount)
  return wrapper
}

describe('HoldingDetailPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders the classic purchase-only table for a holding with no withdrawals', async () => {
    const detail: StockHoldingDetail = {
      id: 'holding-1',
      walletId: 'wallet-1',
      stockTypeId: 'type-1',
      ticker: 'PETR4',
      name: 'Petróleo Brasileiro',
      currentPrice: 34.8,
      lots: [{ id: 'lot-1', lotDate: '2026-01-12', quantity: 300, price: 25.1 }],
      withdrawals: [],
    }
    vi.mocked(holdingsApi.getStockHolding).mockResolvedValue(detail)

    const wrapper = mountPanel(stockRow)
    await flushPromises()

    expect(wrapper.find('th').text()).not.toBe('Tipo')
    expect(wrapper.text()).not.toContain('Resgate')
    expect(wrapper.text()).not.toContain('Saldo')
    expect(wrapper.text()).toContain('Subtotal')
    expect(wrapper.findAll('tbody tr')).toHaveLength(1)
  })

  it('merges purchases and withdrawals into one ledger with signed quantities and a running balance', async () => {
    vi.mocked(holdingsApi.getStockHolding).mockResolvedValue(stockDetailWithWithdrawals())

    const wrapper = mountPanel(stockRow)
    await flushPromises()

    expect(wrapper.text()).toContain('Tipo')
    expect(wrapper.text()).toContain('Saldo')

    const rows = wrapper.findAll('tbody tr')
    expect(rows).toHaveLength(4)

    expect(rows[0].text()).toContain('Compra')
    expect(rows[0].text()).toContain('+300')
    expect(rows[2].text()).toContain('Resgate')
    expect(rows[2].text()).toContain('−150')

    // Balance ends where the outer row's own quantity already says: 300 + 200 - 150 - 150 = 200.
    const lastRowCells = rows[3].findAll('td')
    expect(lastRowCells[lastRowCells.length - 2].text()).toBe('200')
  })

  it('skips the quantity/price/balance columns for a fund and tags its rows as Aporte', async () => {
    const detail: FundHoldingDetail = {
      id: 'holding-2',
      walletId: 'wallet-2',
      fundTypeId: 'type-2',
      name: 'Tesouro IPCA+',
      currentValue: 3000,
      contributions: [{ id: 'c-1', contributionDate: '2026-01-10', amount: 4000 }],
      withdrawals: [
        {
          id: 'w-1',
          resultDate: '2026-05-10',
          quantity: null,
          grossAmount: 1000,
          fees: 5,
          taxes: 10,
          costBasis: 1000,
          netAmount: 985,
          profit: -15,
        },
      ],
    }
    vi.mocked(holdingsApi.getFundHolding).mockResolvedValue(detail)

    const wrapper = mountPanel(fundRow)
    await flushPromises()

    expect(wrapper.text()).toContain('Aporte')
    expect(wrapper.text()).toContain('Resgate')
    expect(wrapper.text()).not.toContain('Qtd.')
    expect(wrapper.text()).not.toContain('Saldo')
    expect(wrapper.text()).toContain('Custos')
    expect(wrapper.text()).toContain('Resultado')
  })
})
