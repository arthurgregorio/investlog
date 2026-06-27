import type { WalletKind, WalletTypeMeta } from '@/types'

export const WALLET_TYPES: Record<WalletKind, WalletTypeMeta> = {
  STOCKS: { label: 'Ações', accent: 'var(--wt-stocks)', icon: 'trending-up' },
  CRYPTO: { label: 'Cripto', accent: 'var(--wt-crypto)', icon: 'bitcoin' },
  FUNDS: { label: 'Fundos', accent: 'var(--wt-funds)', icon: 'office-building-outline' },
}

/** Derives a consistent badge color for a ticker/fund name via a simple hash. */
export function badgeColor(ticker: string | null | undefined, kind: WalletKind): string {
  if (!ticker) return kind === 'FUNDS' ? '#2a8f6f' : '#5b6dd8'
  let hash = 0
  for (let index = 0; index < ticker.length; index++) {
    hash = (hash * 31 + ticker.charCodeAt(index)) >>> 0
  }
  const kindOffset = kind === 'STOCKS' ? 0 : kind === 'CRYPTO' ? 60 : 120
  const hue = (hash % 200) + kindOffset
  return `hsl(${hue}, 55%, 38%)`
}
