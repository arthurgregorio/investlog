import type { HoldingRow } from '@/types'

export type AllocationMetric = 'currentValue' | 'costBasis'

export const OTHERS_SHARE_CUTOFF = 3
export const OTHERS_LABEL = 'Outros'

export interface AllocationEntry {
  key: string
  label: string
  value: number
  share: number
  isOthers: boolean
}

export interface Allocation {
  entries: AllocationEntry[]
  total: number
  excludedCount: number
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
  if (total === 0) return { entries: [], total: 0, excludedCount }

  const individual: AllocationEntry[] = counted.map(({ row, value }) => ({
    key: row.id,
    label: row.ticker ?? row.name,
    value,
    share: (value / total) * 100,
    isOthers: false,
  }))

  const bySizeDescending = (first: AllocationEntry, second: AllocationEntry) =>
    second.share - first.share
  const small = individual.filter((entry) => entry.share < othersShareCutoff)
  const large = individual.filter((entry) => entry.share >= othersShareCutoff)

  if (small.length === 0 || large.length === 0) {
    return { entries: individual.sort(bySizeDescending), total, excludedCount }
  }

  const others: AllocationEntry = {
    key: 'others',
    label: OTHERS_LABEL,
    value: small.reduce((sum, entry) => sum + entry.value, 0),
    share: small.reduce((sum, entry) => sum + entry.share, 0),
    isOthers: true,
  }

  return { entries: [...large.sort(bySizeDescending), others], total, excludedCount }
}

const WHITE_MIX_AT_LIGHTEST_STEP = 0.65
const HEX_COLOR_PATTERN = /^#[0-9a-f]{6}$/i

export function accentShades(accentHex: string, count: number): string[] {
  if (!HEX_COLOR_PATTERN.test(accentHex)) return Array(count).fill(accentHex)
  const channels = [1, 3, 5].map((start) => parseInt(accentHex.slice(start, start + 2), 16))
  return Array.from({ length: count }, (_, index) => {
    const whiteMix = count === 1 ? 0 : (index / (count - 1)) * WHITE_MIX_AT_LIGHTEST_STEP
    const mixed = channels.map((channel) => Math.round(channel + (255 - channel) * whiteMix))
    return `#${mixed.map((channel) => channel.toString(16).padStart(2, '0')).join('')}`
  })
}
