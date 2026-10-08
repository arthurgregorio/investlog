import { WALLET_TYPES } from '@/utils/walletTypes'
import type { KindSummary, WalletKind } from '@/types'

export interface WalletKindRow {
  key: WalletKind
  label: string
  accent: string
  icon: string
  invested: number
  currentValue: number
  gain: number | null
  gainPct: number | null
  walletCount: number
  holdings: number
}

const ALL_KINDS: WalletKind[] = ['STOCKS', 'CRYPTO', 'FUNDS']

export function walletKindRows(
  kindSummaries: KindSummary[],
  wallets: { kind: WalletKind }[],
): WalletKindRow[] {
  const summaryByKind = new Map(kindSummaries.map((kindSummary) => [kindSummary.kind, kindSummary]))
  return ALL_KINDS.map((kind) => {
    const kindSummary = summaryByKind.get(kind)
    return {
      key: kind,
      label: WALLET_TYPES[kind].label,
      accent: WALLET_TYPES[kind].accent,
      icon: WALLET_TYPES[kind].icon,
      invested: kindSummary?.totalCostBasis ?? 0,
      currentValue: kindSummary?.totalCurrentValue ?? 0,
      gain: kindSummary ? kindSummary.totalGain : null,
      gainPct: kindSummary ? kindSummary.totalGainPct : null,
      walletCount: wallets.filter((wallet) => wallet.kind === kind).length,
      holdings: kindSummary?.holdingCount ?? 0,
    }
  })
}
