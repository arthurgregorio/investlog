import type { HoldingRow } from '@/types'

export type AllocationMetric = 'currentValue' | 'costBasis'

export type AllocationGrouping = 'holding' | 'segment'

export const OTHERS_SHARE_CUTOFF = 3
export const OTHERS_LABEL = 'Outros'

export const SEGMENTLESS_KEY = 'segmentless'
export const SEGMENTLESS_LABEL = 'Sem segmento'

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

interface CountedRow {
  row: HoldingRow
  value: number
}

function metricValue(row: HoldingRow, metric: AllocationMetric): number | null {
  return metric === 'currentValue' ? row.currentValue : row.costBasis
}

function investmentCount(count: number): string {
  return count === 1 ? '1 investimento' : `${count} investimentos`
}

function segmentCount(count: number): string {
  return count === 1 ? '1 segmento' : `${count} segmentos`
}

function holdingEntries(counted: CountedRow[], total: number): AllocationEntry[] {
  return counted.map(({ row, value }) => ({
    key: row.id,
    label: row.ticker ?? row.name,
    name: row.ticker && row.name && row.name !== row.ticker ? row.name : null,
    value,
    share: (value / total) * 100,
    isOthers: false,
  }))
}

function segmentEntries(counted: CountedRow[], total: number): AllocationEntry[] {
  const groups = new Map<string, { label: string; value: number; count: number }>()
  for (const { row, value } of counted) {
    const key = row.segmentLabel == null ? SEGMENTLESS_KEY : `segment:${row.segmentLabel}`
    const group = groups.get(key) ?? {
      label: row.segmentLabel ?? SEGMENTLESS_LABEL,
      value: 0,
      count: 0,
    }
    group.value += value
    group.count += 1
    groups.set(key, group)
  }
  return [...groups].map(([key, group]) => ({
    key,
    label: group.label,
    name: investmentCount(group.count),
    value: group.value,
    share: (group.value / total) * 100,
    isOthers: false,
  }))
}

export function computeAllocation(
  rows: HoldingRow[],
  metric: AllocationMetric,
  othersShareCutoff: number = OTHERS_SHARE_CUTOFF,
  grouping: AllocationGrouping = 'holding',
): Allocation {
  const excludedCount = rows.filter((row) => metricValue(row, metric) == null).length
  const counted = rows
    .map((row) => ({ row, value: metricValue(row, metric) }))
    .filter((item): item is CountedRow => item.value != null && item.value > 0)

  const total = counted.reduce((sum, item) => sum + item.value, 0)
  if (total === 0) return { entries: [], total: 0, excludedCount, topThreeShare: null }

  const individual =
    grouping === 'segment' ? segmentEntries(counted, total) : holdingEntries(counted, total)

  const bySizeDescending = (first: AllocationEntry, second: AllocationEntry) =>
    second.share - first.share
  const topThreeShare =
    grouping === 'holding' && individual.length > TOP_POSITIONS_COUNT
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
    name: grouping === 'segment' ? segmentCount(small.length) : investmentCount(small.length),
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
