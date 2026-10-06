import type { HoldingRow } from '@/types'

export type AllocationMetric = 'currentValue' | 'costBasis'

export const OTHERS_SHARE_CUTOFF = 3
export const OTHERS_LABEL = 'Outros'

export const TOP_POSITIONS_COUNT = 3

export interface AllocationEntry {
  key: string
  label: string
  name: string | null
  value: number
  share: number
  isOthers: boolean
}

export interface Allocation {
  entries: AllocationEntry[]
  total: number
  excludedCount: number
  topThreeShare: number | null
}

function metricValue(row: HoldingRow, metric: AllocationMetric): number | null {
  return metric === 'currentValue' ? row.currentValue : row.costBasis
}

export function computeAllocation(
  rows: HoldingRow[],
  metric: AllocationMetric,
  othersShareCutoff: number = OTHERS_SHARE_CUTOFF,
): Allocation {
  const excludedCount = rows.filter((row) => metricValue(row, metric) == null).length
  const counted = rows
    .map((row) => ({ row, value: metricValue(row, metric) }))
    .filter(
      (item): item is { row: HoldingRow; value: number } => item.value != null && item.value > 0,
    )

  const total = counted.reduce((sum, item) => sum + item.value, 0)
  if (total === 0) return { entries: [], total: 0, excludedCount, topThreeShare: null }

  const individual: AllocationEntry[] = counted.map(({ row, value }) => ({
    key: row.id,
    label: row.ticker ?? row.name,
    name: row.ticker && row.name && row.name !== row.ticker ? row.name : null,
    value,
    share: (value / total) * 100,
    isOthers: false,
  }))

  const bySizeDescending = (first: AllocationEntry, second: AllocationEntry) =>
    second.share - first.share
  const topThreeShare =
    individual.length > TOP_POSITIONS_COUNT
      ? [...individual]
          .sort(bySizeDescending)
          .slice(0, TOP_POSITIONS_COUNT)
          .reduce((sum, entry) => sum + entry.share, 0)
      : null
  const small = individual.filter((entry) => entry.share < othersShareCutoff)
  const large = individual.filter((entry) => entry.share >= othersShareCutoff)

  if (small.length === 0 || large.length === 0) {
    return { entries: individual.sort(bySizeDescending), total, excludedCount, topThreeShare }
  }

  const others: AllocationEntry = {
    key: 'others',
    label: OTHERS_LABEL,
    name: small.length === 1 ? '1 investimento' : `${small.length} investimentos`,
    value: small.reduce((sum, entry) => sum + entry.value, 0),
    share: small.reduce((sum, entry) => sum + entry.share, 0),
    isOthers: true,
  }

  return {
    entries: [...large.sort(bySizeDescending), others],
    total,
    excludedCount,
    topThreeShare,
  }
}

export const CHART_PALETTE_SIZE = 10

export function chartColor(index: number): string {
  return `var(--chart-${(index % CHART_PALETTE_SIZE) + 1})`
}
