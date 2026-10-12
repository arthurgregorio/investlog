import type { HoldingRow } from '@/types'

export function holdingDisplayName(holding: HoldingRow): string {
  return holding.ticker ?? holding.name
}

export function compareDisplayNames(first: string, second: string): number {
  return first.localeCompare(second, 'pt-BR', { sensitivity: 'base', numeric: true })
}

export function sortHoldingsByDisplayName(holdings: HoldingRow[]): HoldingRow[] {
  return [...holdings].sort((first, second) =>
    compareDisplayNames(holdingDisplayName(first), holdingDisplayName(second)),
  )
}
