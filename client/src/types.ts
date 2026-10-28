/* InvestLog domain types — API response shapes and shared presentation types. */

/** The three wallet/holding categories. */
export type WalletKind = 'STOCKS' | 'CRYPTO' | 'FUNDS'

export interface WalletTypeMeta {
  label: string
  accent: string
  icon: string
}

/** Accent keys map to the [data-accent] CSS variants. */
export type AccentKey = 'blue' | 'indigo' | 'teal' | 'yellow'

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
  segmentLabel: string | null
  walletId: string
  walletName: string
  walletCurrency: string
  quantity: number | null
  costBasis: number
  currentPrice: number | null
  currentValue: number | null
  gain: number | null
  gainPct: number | null
  frozen: boolean
  strategyId?: string | null
  strategyName?: string | null
  strategyClassId?: string | null
  strategyClassName?: string | null
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
  displayCurrency: string
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
  usageCount: number
}

export type HoldingType = AssetType & { kinds: WalletKind[] }

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

/** One recorded exit from a holding, embedded in its detail response. */
export type ResultType = 'WITHDRAWAL' | 'REINVESTMENT'

export interface WithdrawalDetail {
  id: string
  resultType: ResultType
  resultDate: string // ISO yyyy-mm-dd
  quantity: number | null
  grossAmount: number
  fees: number
  taxes: number
  costBasis: number
  netAmount: number
  profit: number
}

export interface StockHoldingDetail extends Partial<HoldingStrategyTags> {
  id: string
  walletId: string
  typeId: string
  typeName: string
  segmentId: string | null
  segmentName: string | null
  ticker: string
  name: string
  currentPrice: number | null
  lots: LotDetail[]
  frozen: boolean
  withdrawals: WithdrawalDetail[]
}

export interface CryptoHoldingDetail extends Partial<HoldingStrategyTags> {
  id: string
  walletId: string
  typeId: string | null
  typeName: string | null
  segmentId: string | null
  segmentName: string | null
  ticker: string
  name: string
  currentPrice: number | null
  lots: LotDetail[]
  frozen: boolean
  withdrawals: WithdrawalDetail[]
}

export interface FundHoldingDetail extends Partial<HoldingStrategyTags> {
  id: string
  walletId: string
  typeId: string
  typeName: string
  segmentId: string | null
  segmentName: string | null
  name: string
  currentValue: number | null
  administrationFeeRate: number | null
  performanceFeeRate: number | null
  contributions: ContributionDetail[]
  frozen: boolean
  withdrawals: WithdrawalDetail[]
}

export type HoldingDetail = StockHoldingDetail | CryptoHoldingDetail | FundHoldingDetail

/** One recorded exit from a position — a row of `GET /results`. */
export interface ResultRow {
  id: string
  kind: WalletKind
  resultType: ResultType
  holdingName: string
  ticker: string | null
  walletId: string
  walletName: string
  walletCurrency: string
  resultDate: string
  quantity: number | null
  grossAmount: number
  fees: number
  taxes: number
  costBasis: number
  netAmount: number
  profit: number
}

/** Realised totals over every recorded result, in the user's display currency — `GET /results/summary`. */
export interface ResultSummary {
  displayCurrency: string
  totalWithdrawn: number
  totalNetReceived: number
  totalProfit: number
  totalFees: number
  totalTaxes: number
  exitCount: number
}

/** One end of a reinvestment: the holding it left or the holding it landed in. */
export interface ReinvestmentSide {
  holdingId: string
  kind: WalletKind
  name: string
  ticker: string | null
  walletId: string
  walletName: string
}

/** One recorded reinvestment — a row of `GET /reinvestments`. */
export interface ReinvestmentRow {
  id: string
  reinvestmentDate: string
  currency: string
  source: ReinvestmentSide
  destination: ReinvestmentSide
  quantity: number | null
  grossAmount: number
  fees: number
  taxes: number
  amount: number
  profit: number
}

/** One end-of-day point of a wallet's value history. */
export interface WalletSnapshotPoint {
  snapshotDate: string
  currentValue: number
  totalInvested: number
  gain: number
  gainPct: number
}

/** The best or worst performing holding in a wallet. */
export interface WalletPerformer {
  id: string
  name: string
  ticker: string | null
  kind: WalletKind
  gain: number
  gainPct: number
}

export interface WalletActivity {
  lastTransactionDate: string | null
  lastTransactionName: string | null
  lastTransactionAmount: number | null
  transactionCount: number
  walletAgeInDays: number | null
  investmentCount: number
}

/** Everything the wallet detail page shows, from `GET /wallets/{id}/detail`. */
export interface WalletDetail {
  id: string
  name: string
  kind: WalletKind
  currency: string
  currentValue: number
  totalInvested: number
  gain: number
  gainPct: number | null
  series: WalletSnapshotPoint[]
  dayChange: number | null
  weekChange: number | null
  monthChange: number | null
  bestPerformer: WalletPerformer | null
  worstPerformer: WalletPerformer | null
  largestHoldingName: string | null
  largestHoldingShare: number | null
  activity: WalletActivity
}

export interface PagedResponse<T> {
  content: T[]
  page: {
    size: number
    number: number
    totalElements: number
    totalPages: number
  }
}

export type UserRole = 'ADMIN' | 'USER'

export type UserStatus = 'PENDING' | 'APPROVED' | 'BLOCKED'

export interface SessionResponse {
  name: string
  email: string
  role: UserRole
  status: UserStatus
  authProvider: 'LOCAL' | 'GOOGLE'
  demoModeEnabled: boolean
}

export interface AuthConfigResponse {
  googleAuthEnabled: boolean
  demoModeEnabled: boolean
}

export interface UserAdminResponse {
  id: string
  name: string
  email: string
  role: UserRole
  status: UserStatus
  authProvider: 'LOCAL' | 'GOOGLE'
  totpEnabled: boolean
}

export interface TotpEnrollResponse {
  secretKey: string
  qrCodeDataUri: string
}

export interface TrustedDeviceResponse {
  id: string
  label: string
  lastUsedAt: string
  expiresAt: string
}

export type LoginOutcome =
  | { status: 'authenticated'; session: SessionResponse }
  | { status: 'needs_enrollment' }
  | { status: 'totp_required' }
  | { status: 'invalid_totp_code' }

export interface ProfileResponse {
  name: string
  email: string
  avatarUrl: string | null
  accentColor: string
  preferredCurrency: string
}

export interface ProfileUpdateRequest {
  accentColor?: string
  preferredCurrency?: string
}

export interface ConfigurationResponse {
  key: string
  value: string
  updatedAt: string
}

export type WalletLedgerEventType =
  'PURCHASE' | 'CONTRIBUTION' | 'WITHDRAWAL' | 'REINVESTMENT' | 'MOVE_IN' | 'MOVE_OUT'

/** One event that touched a wallet — a row of `GET /wallets/{id}/ledger`. */
export interface WalletLedgerEntry {
  id: string
  date: string
  type: WalletLedgerEventType
  kind: WalletKind
  holdingId: string | null
  holdingName: string
  ticker: string | null
  quantity: number | null
  amount: number | null
  profit: number | null
  counterpartName: string | null
}

export interface HoldingStrategyTags {
  strategyId: string
  strategyName: string
  strategyClassId: string
  strategyClassName: string
}

export interface StrategyClass {
  id: string
  name: string
  targetPercentage: number | null
  position: number
  default: boolean
  holdingCount: number
  typedCount: number
  typedPercentageSum: number
  blankSharePercentage: number | null
}

export interface Strategy {
  id: string
  name: string
  default: boolean
  holdingCount: number
  classes: StrategyClass[]
}

export interface ClassHolding {
  kind: WalletKind
  id: string
  walletId: string
  walletName: string
  ticker: string | null
  name: string
  typeLabel: string | null
  segmentLabel: string | null
  frozen: boolean
  targetPercentage: number | null
  effectiveTargetPercentage: number
}
