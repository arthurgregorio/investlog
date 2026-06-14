/* InvestLog domain types — a manual investment logbook.
   Money invested = cost basis. "Current value" is an OPTIONAL manual field,
   so gain/loss only appears where the user filled it in. */

export type WalletKind = 'stocks' | 'crypto' | 'funds'

export interface Wallet {
  id: string
  name: string
  type: WalletKind
  currency: string
}

/** A dated purchase of a stock/crypto position. */
export interface Lot {
  id: string
  date: string // ISO yyyy-mm-dd
  qty: number
  price: number
}

/** A dated contribution to a fund position. */
export interface Contribution {
  id: string
  date: string // ISO yyyy-mm-dd
  amount: number
}

interface HoldingBase {
  id: string
  walletId: string
  name: string
}

export interface StockHolding extends HoldingBase {
  kind: 'stock'
  stockType: string
  ticker: string
  currentPrice?: number
  lots: Lot[]
}

export interface CryptoHolding extends HoldingBase {
  kind: 'crypto'
  ticker: string
  currentPrice?: number
  lots: Lot[]
}

export interface FundHolding extends HoldingBase {
  kind: 'fund'
  fundType: string
  currentValue?: number
  contributions: Contribution[]
}

export type Holding = StockHolding | CryptoHolding | FundHolding

export interface CurrencyConfig {
  base: string
  /** value of 1 unit of the currency expressed in the BASE currency */
  rates: Record<string, number>
}

export interface WalletTypeMeta {
  label: string
  accent: string
  icon: string
}

/** Accent keys map to the [data-accent] CSS variants. */
export type AccentKey = 'blue' | 'indigo' | 'teal' | 'green'

export interface Gain {
  value: number
  pct: number
}

export interface TypeTotal {
  key: WalletKind
  label: string
  accent: string
  invested: number
  wallets: number
  holdings: number
}
