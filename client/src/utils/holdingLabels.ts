import type { HoldingRow } from '@/types'

export function holdingTypeLabel(holding: Pick<HoldingRow, 'kind' | 'typeLabel'>): string {
  if (holding.kind === 'CRYPTO') return 'Cripto'
  return holding.typeLabel ?? (holding.kind === 'FUNDS' ? 'Fundo' : 'Ação')
}
