import type { WalletDetail } from '@/types'

export function walletDetailOf(overrides: Partial<WalletDetail> = {}): WalletDetail {
  return {
    id: 'wallet-1',
    name: 'Detail Wallet',
    kind: 'STOCKS',
    currency: 'BRL',
    currentValue: 4750,
    totalInvested: 4500,
    gain: 250,
    gainPct: 5.5556,
    series: [],
    dayChange: null,
    weekChange: null,
    monthChange: null,
    bestPerformer: null,
    worstPerformer: null,
    largestHoldingName: null,
    largestHoldingShare: null,
    activity: {
      lastTransactionDate: null,
      lastTransactionName: null,
      lastTransactionAmount: null,
      transactionCount: 0,
      walletAgeInDays: null,
      investmentCount: 2,
    },
    ...overrides,
  }
}
