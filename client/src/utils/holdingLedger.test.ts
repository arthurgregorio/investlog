import { describe, expect, it } from 'vitest'
import { buildLedger } from '@/utils/holdingLedger'
import type { StockHoldingDetail, FundHoldingDetail } from '@/types'

function stockDetail(overrides: Partial<StockHoldingDetail> = {}): StockHoldingDetail {
  return {
    id: 'holding-1',
    walletId: 'wallet-1',
    stockTypeId: 'type-1',
    ticker: 'PETR4',
    name: 'Petróleo Brasileiro',
    currentPrice: 34.8,
    lots: [],
    withdrawals: [],
    ...overrides,
  }
}

function fundDetail(overrides: Partial<FundHoldingDetail> = {}): FundHoldingDetail {
  return {
    id: 'holding-2',
    walletId: 'wallet-1',
    fundTypeId: 'type-2',
    name: 'Tesouro IPCA+',
    currentValue: 4000,
    contributions: [],
    withdrawals: [],
    ...overrides,
  }
}

describe('buildLedger', () => {
  it('returns only purchase rows, sorted, with a running balance, when there are no withdrawals', () => {
    const detail = stockDetail({
      lots: [
        { id: 'lot-2', lotDate: '2026-03-03', quantity: 200, price: 33 },
        { id: 'lot-1', lotDate: '2026-01-12', quantity: 300, price: 25.1 },
      ],
    })

    const rows = buildLedger(detail, false)

    expect(rows.map((row) => row.id)).toEqual(['lot-1', 'lot-2'])
    expect(rows.every((row) => row.type === 'PURCHASE')).toBe(true)
    expect(rows.every((row) => row.costs === null && row.profit === null)).toBe(true)
    expect(rows[0].balance).toBe(300)
    expect(rows[1].balance).toBe(500)
  })

  it('interleaves purchases and withdrawals by date with a signed quantity and running balance', () => {
    const detail = stockDetail({
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
    })

    const rows = buildLedger(detail, false)

    expect(rows.map((row) => row.id)).toEqual(['lot-1', 'lot-2', 'w-1', 'w-2'])
    expect(rows.map((row) => row.balance)).toEqual([300, 500, 350, 200])

    const firstWithdrawal = rows[2]
    expect(firstWithdrawal.type).toBe('WITHDRAWAL')
    expect(firstWithdrawal.quantity).toBe(-150)
    expect(firstWithdrawal.unitPrice).toBeCloseTo(30.5)
    expect(firstWithdrawal.costs).toBe(130)
    expect(firstWithdrawal.amount).toBe(4445)
    expect(firstWithdrawal.profit).toBe(206)
  })

  it('never fills balance for a fund, since currentValue can drift from the ledger total', () => {
    const detail = fundDetail({
      contributions: [{ id: 'c-1', contributionDate: '2026-01-10', amount: 5000 }],
      withdrawals: [
        {
          id: 'w-1',
          resultDate: '2026-05-10',
          quantity: null,
          grossAmount: 2500,
          fees: 0,
          taxes: 0,
          costBasis: 1250,
          netAmount: 2500,
          profit: 1250,
        },
      ],
    })

    const rows = buildLedger(detail, true)

    expect(rows.every((row) => row.balance === null)).toBe(true)
    expect(rows.every((row) => row.quantity === null && row.unitPrice === null)).toBe(true)
    expect(rows[1].amount).toBe(2500)
  })
})
