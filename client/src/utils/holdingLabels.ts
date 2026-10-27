import type { HoldingRow, WalletKind } from '@/types'

const FALLBACK_TYPE_LABELS: Record<WalletKind, string> = {
  STOCKS: 'Ação',
  CRYPTO: 'Cripto',
  FUNDS: 'Fundo',
}

export function holdingTypeLabel(holding: Pick<HoldingRow, 'kind' | 'typeLabel'>): string {
  return holding.typeLabel ?? FALLBACK_TYPE_LABELS[holding.kind]
}
