import type { HoldingRow, WalletKind } from '@/types'

export interface ReportRow {
  holding: HoldingRow
  currentPrice: number | null
  costBasis: number
  currentValue: number
  gain: number
  gainPct: number | null
}

export interface ReportTotals {
  costBasis: number
  currentValue: number
  gain: number
  gainPct: number | null
}

export interface WalletGroup {
  walletId: string
  walletName: string
  rows: ReportRow[]
  totals: ReportTotals
}

export interface SubGroup {
  key: string
  label: string
  walletGroups: WalletGroup[]
  totals: ReportTotals
}

export interface KindGroup {
  kind: WalletKind
  label: string
  subGroups: SubGroup[]
  totals: ReportTotals
}

export interface ReportGrouping {
  kindGroups: KindGroup[]
  grandTotals: ReportTotals
}

const KIND_ORDER: { kind: WalletKind; label: string }[] = [
  { kind: 'STOCKS', label: 'Ações' },
  { kind: 'CRYPTO', label: 'Cripto' },
  { kind: 'FUNDS', label: 'Fundos' },
]

function totalsOf(rows: { costBasis: number; currentValue: number }[]): ReportTotals {
  const costBasis = rows.reduce((sum, row) => sum + row.costBasis, 0)
  const currentValue = rows.reduce((sum, row) => sum + row.currentValue, 0)
  const gain = currentValue - costBasis
  const gainPct = costBasis !== 0 ? (gain / costBasis) * 100 : null
  return { costBasis, currentValue, gain, gainPct }
}

function subGroupKey(holding: HoldingRow): { key: string; label: string } {
  if (holding.kind === 'CRYPTO') {
    const ticker = holding.ticker ?? holding.name
    return { key: ticker, label: ticker }
  }
  const label = holding.typeLabel ?? 'Outros'
  return { key: label, label }
}

/**
 * Purely presentational: nests the already backend-merged flat rows into
 * Kind -> Type/Ticker -> Wallet, with a subtotal at every level and one grand total.
 * No dedup/merge logic here — same-wallet same-ticker merging already happened in the
 * finances.holdings_report_rows SQL view.
 */
export function groupHoldingsForReport(
  holdings: HoldingRow[],
  convert: (amount: number, fromCurrency: string) => number,
): ReportGrouping {
  const reportRows: ReportRow[] = holdings.map((holding) => {
    const costBasis = convert(holding.costBasis, holding.walletCurrency)
    const currentValue = convert(holding.currentValue ?? 0, holding.walletCurrency)
    const currentPrice =
      holding.currentPrice == null ? null : convert(holding.currentPrice, holding.walletCurrency)
    return {
      holding,
      currentPrice,
      costBasis,
      currentValue,
      gain: currentValue - costBasis,
      gainPct: costBasis !== 0 ? ((currentValue - costBasis) / costBasis) * 100 : null,
    }
  })

  const kindGroups: KindGroup[] = KIND_ORDER.filter(({ kind }) =>
    reportRows.some((row) => row.holding.kind === kind),
  ).map(({ kind, label }) => {
    const kindRows = reportRows.filter((row) => row.holding.kind === kind)

    const subGroupKeys = [...new Set(kindRows.map((row) => subGroupKey(row.holding).key))].sort(
      (a, b) => a.localeCompare(b),
    )

    const subGroups: SubGroup[] = subGroupKeys.map((key) => {
      const subGroupRows = kindRows.filter((row) => subGroupKey(row.holding).key === key)
      const subGroupLabel = subGroupKey(subGroupRows[0].holding).label

      const walletIds = [...new Set(subGroupRows.map((row) => row.holding.walletId))].sort(
        (a, b) => {
          const walletNameA = subGroupRows.find((row) => row.holding.walletId === a)!.holding
            .walletName
          const walletNameB = subGroupRows.find((row) => row.holding.walletId === b)!.holding
            .walletName
          return walletNameA.localeCompare(walletNameB)
        },
      )

      const walletGroups: WalletGroup[] = walletIds.map((walletId) => {
        const walletRows = subGroupRows
          .filter((row) => row.holding.walletId === walletId)
          .sort((a, b) =>
            (a.holding.ticker ?? a.holding.name).localeCompare(b.holding.ticker ?? b.holding.name),
          )

        return {
          walletId,
          walletName: walletRows[0].holding.walletName,
          rows: walletRows,
          totals: totalsOf(walletRows),
        }
      })

      return {
        key,
        label: subGroupLabel,
        walletGroups,
        totals: totalsOf(subGroupRows),
      }
    })

    return {
      kind,
      label,
      subGroups,
      totals: totalsOf(kindRows),
    }
  })

  return {
    kindGroups,
    grandTotals: totalsOf(reportRows),
  }
}
