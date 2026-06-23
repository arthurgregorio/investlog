/* InvestLog domain types — API response shapes and shared presentation types. */

/** The three wallet/holding categories. */
export type WalletKind = 'STOCKS' | 'CRYPTO' | 'FUNDS'

export interface WalletTypeMeta {
  label: string
  accent: string
  icon: string
}

/** Accent keys map to the [data-accent] CSS variants. */
export type AccentKey = 'blue' | 'indigo' | 'teal' | 'green'

// --- API response types (match backend response payloads exactly) ---

export interface WalletResponse {
  id: string
  name: string
  kind: WalletKind
  currency: string
  holdingCount: number
  totalInvested: number
  currentValue: number | null
  gain: number | null
  gainPct: number | null
  createdAt: string
}

export interface HoldingRow {
  id: string
  kind: WalletKind
  name: string
  ticker: string | null
  typeLabel: string | null
  walletId: string
  walletName: string
  walletCurrency: string
  quantity: number | null
  costBasis: number
  currentPrice: number | null
  currentValue: number | null
  gain: number | null
  gainPct: number | null
}

export interface KindSummary {
  kind: string
  holdingCount: number
  totalCostBasis: number
  totalCurrentValue: number
  totalGain: number
  totalGainPct: number | null
}

export interface PortfolioSummary {
  baseCurrency: string | null
  totalCostBasis: number
  totalCurrentValue: number
  totalGain: number
  totalGainPct: number | null
  kindSummaries: KindSummary[]
}

export interface SeriesPoint {
  month: string // "YYYY-MM"
  totalInvested: number
}

export interface AssetType {
  id: string
  name: string
}

export interface CurrencyRate {
  currencyCode: string
  rate: number
  isBase: boolean
}

export interface LotDetail {
  id: string
  lotDate: string // ISO yyyy-mm-dd
  quantity: number
  price: number
}

export interface ContributionDetail {
  id: string
  contributionDate: string // ISO yyyy-mm-dd
  amount: number
}

export interface StockHoldingDetail {
  id: string
  walletId: string
  stockTypeId: string
  ticker: string
  name: string
  currentPrice: number | null
  lots: LotDetail[]
}

export interface CryptoHoldingDetail {
  id: string
  walletId: string
  ticker: string
  name: string
  currentPrice: number | null
  lots: LotDetail[]
}

export interface FundHoldingDetail {
  id: string
  walletId: string
  fundTypeId: string
  name: string
  currentValue: number | null
  contributions: ContributionDetail[]
}

export type HoldingDetail = StockHoldingDetail | CryptoHoldingDetail | FundHoldingDetail

export interface PagedResponse<T> {
  content: T[]
  page: {
    size: number
    number: number
    totalElements: number
    totalPages: number
  }
}

export interface ProfileResponse {
  name: string
  email: string
  avatarUrl: string | null
}
