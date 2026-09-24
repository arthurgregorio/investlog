import type { HoldingDetail, StockHoldingDetail, CryptoHoldingDetail, FundHoldingDetail } from '@/types'

export type LedgerMovementType = 'PURCHASE' | 'WITHDRAWAL'

export interface LedgerRow {
  type: LedgerMovementType
  id: string
  date: string
  quantity: number | null
  unitPrice: number | null
  fees: number | null
  taxes: number | null
  costs: number | null
  amount: number
  profit: number | null
  balance: number | null
}

/**
 * Merges a holding's purchases/aportes and withdrawals into one chronological ledger.
 * `balance` (the running quantity) is only meaningful for stocks/crypto — a fund's
 * currentValue can be overridden independently of its contributions/withdrawals via
 * UpdatePriceModal, so a running total here would drift from the value the holding
 * row already shows and is left null.
 */
export function buildLedger(detail: HoldingDetail, isFund: boolean): LedgerRow[] {
  const purchaseRows: LedgerRow[] = isFund
    ? (detail as FundHoldingDetail).contributions.map((contribution) => ({
        type: 'PURCHASE',
        id: contribution.id,
        date: contribution.contributionDate,
        quantity: null,
        unitPrice: null,
        fees: null,
        taxes: null,
        costs: null,
        amount: contribution.amount,
        profit: null,
        balance: null,
      }))
    : (detail as StockHoldingDetail | CryptoHoldingDetail).lots.map((lot) => ({
        type: 'PURCHASE',
        id: lot.id,
        date: lot.lotDate,
        quantity: lot.quantity,
        unitPrice: lot.price,
        fees: null,
        taxes: null,
        costs: null,
        amount: lot.quantity * lot.price,
        profit: null,
        balance: null,
      }))

  const withdrawalRows: LedgerRow[] = detail.withdrawals.map((withdrawal) => ({
    type: 'WITHDRAWAL',
    id: withdrawal.id,
    date: withdrawal.resultDate,
    quantity: withdrawal.quantity == null ? null : -withdrawal.quantity,
    unitPrice:
      withdrawal.quantity == null || withdrawal.quantity === 0
        ? null
        : withdrawal.grossAmount / withdrawal.quantity,
    fees: withdrawal.fees,
    taxes: withdrawal.taxes,
    costs: withdrawal.fees + withdrawal.taxes,
    amount: withdrawal.netAmount,
    profit: withdrawal.profit,
    balance: null,
  }))

  const rows = [...purchaseRows, ...withdrawalRows].sort((a, b) => a.date.localeCompare(b.date))

  if (isFund) return rows

  let runningBalance = 0
  for (const row of rows) {
    runningBalance += row.quantity ?? 0
    row.balance = runningBalance
  }
  return rows
}
