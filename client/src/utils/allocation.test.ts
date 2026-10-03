import { describe, expect, it } from 'vitest'
import { accentShades, computeAllocation, OTHERS_LABEL, OTHERS_SHARE_CUTOFF } from './allocation'
import type { HoldingRow } from '@/types'

function rowOf(overrides: Partial<HoldingRow>): HoldingRow {
  return {
    id: 'holding-1',
    kind: 'STOCKS',
    name: 'Petrobras',
    ticker: 'PETR4',
    typeLabel: 'Ação ON',
    walletId: 'wallet-1',
    walletName: 'Wallet',
    walletCurrency: 'BRL',
    quantity: 10,
    costBasis: 100,
    currentPrice: 10,
    currentValue: 100,
    gain: 0,
    gainPct: 0,
    frozen: false,
    ...overrides,
  }
}

function sumOfShares(entries: { share: number }[]) {
  return entries.reduce((sum, entry) => sum + entry.share, 0)
}

describe('accentShades', () => {
  it('runs from the accent itself down to a lighter tint', () => {
    const shades = accentShades('#2b6cb0', 3)

    expect(shades[0]).toBe('#2b6cb0')
    expect(shades).toHaveLength(3)
    expect(new Set(shades).size).toBe(3)
    const lightness = (hex: string) =>
      [1, 3, 5].reduce((sum, start) => sum + parseInt(hex.slice(start, start + 2), 16), 0)
    expect(lightness(shades[0])).toBeLessThan(lightness(shades[1]))
    expect(lightness(shades[1])).toBeLessThan(lightness(shades[2]))
  })

  it('returns the accent unchanged for a single step or a non-hex colour', () => {
    expect(accentShades('#0ca678', 1)).toEqual(['#0ca678'])
    expect(accentShades('', 2)).toEqual(['', ''])
  })
})

describe('computeAllocation', () => {
  it('returns shares that sum to 100 and are ordered descending', () => {
    const rows = [
      rowOf({ id: 'a', ticker: 'AAAA3', currentValue: 200 }),
      rowOf({ id: 'b', ticker: 'BBBB3', currentValue: 500 }),
      rowOf({ id: 'c', ticker: 'CCCC3', currentValue: 300 }),
    ]

    const { entries, total } = computeAllocation(rows, 'currentValue')

    expect(entries.map((entry) => entry.label)).toEqual(['BBBB3', 'CCCC3', 'AAAA3'])
    expect(entries.map((entry) => entry.share)).toEqual([50, 30, 20])
    expect(sumOfShares(entries)).toBeCloseTo(100, 9)
    expect(total).toBe(1000)
  })

  it('uses the fund name as the label when the row has no ticker', () => {
    const { entries } = computeAllocation(
      [rowOf({ ticker: null, name: 'Fundo Renda Fixa', currentValue: 10 })],
      'currentValue',
    )

    expect(entries[0].label).toBe('Fundo Renda Fixa')
  })

  it('recomputes from cost basis when the metric changes', () => {
    const rows = [
      rowOf({ id: 'a', ticker: 'AAAA3', costBasis: 900, currentValue: 100 }),
      rowOf({ id: 'b', ticker: 'BBBB3', costBasis: 100, currentValue: 900 }),
    ]

    const current = computeAllocation(rows, 'currentValue').entries
    const invested = computeAllocation(rows, 'costBasis').entries

    expect(current.map((entry) => [entry.label, entry.share])).toEqual([
      ['BBBB3', 90],
      ['AAAA3', 10],
    ])
    expect(invested.map((entry) => [entry.label, entry.share])).toEqual([
      ['AAAA3', 90],
      ['BBBB3', 10],
    ])
  })

  it('excludes rows without a current value from the current value metric and counts them', () => {
    const rows = [
      rowOf({ id: 'a', ticker: 'AAAA3', currentValue: 100 }),
      rowOf({ id: 'b', ticker: 'BBBB3', currentValue: null, costBasis: 900 }),
    ]

    const current = computeAllocation(rows, 'currentValue')
    const invested = computeAllocation(rows, 'costBasis')

    expect(current.entries.map((entry) => entry.label)).toEqual(['AAAA3'])
    expect(current.entries[0].share).toBe(100)
    expect(current.excludedCount).toBe(1)
    expect(invested.entries.map((entry) => entry.label)).toEqual(['BBBB3', 'AAAA3'])
    expect(invested.excludedCount).toBe(0)
  })

  it('groups holdings under the cutoff into a single Outros entry that keeps the total at 100', () => {
    const rows = [
      rowOf({ id: 'big', ticker: 'BIGG3', currentValue: 9000 }),
      rowOf({ id: 'small-1', ticker: 'SML1', currentValue: 100 }),
      rowOf({ id: 'small-2', ticker: 'SML2', currentValue: 100 }),
    ]

    const { entries } = computeAllocation(rows, 'currentValue')

    expect(OTHERS_SHARE_CUTOFF).toBe(3)
    expect(entries.map((entry) => entry.label)).toEqual(['BIGG3', OTHERS_LABEL])
    expect(entries[1].isOthers).toBe(true)
    expect(entries[1].value).toBe(200)
    expect(sumOfShares(entries)).toBeCloseTo(100, 9)
  })

  it('keeps Outros last in the legend even when it outweighs the other entries', () => {
    const smallRows = Array.from({ length: 20 }, (_, index) =>
      rowOf({ id: `small-${index}`, ticker: `SML${index}`, currentValue: 29 }),
    )
    const rows = [
      rowOf({ id: 'big', ticker: 'BIGG3', currentValue: 400 }),
      rowOf({ id: 'mid', ticker: 'MIDD3', currentValue: 300 }),
      ...smallRows,
    ]

    const { entries } = computeAllocation(rows, 'currentValue')

    expect(entries.map((entry) => entry.label)).toEqual(['BIGG3', 'MIDD3', OTHERS_LABEL])
    expect(entries[2].share).toBeGreaterThan(entries[0].share)
    expect(sumOfShares(entries)).toBeCloseTo(100, 9)
  })

  it('collapses a single holding under the cutoff into Outros', () => {
    const rows = [
      rowOf({ id: 'big', ticker: 'BIGG3', currentValue: 9900 }),
      rowOf({ id: 'small', ticker: 'SML1', currentValue: 100 }),
    ]

    const { entries } = computeAllocation(rows, 'currentValue')

    expect(entries.map((entry) => entry.label)).toEqual(['BIGG3', OTHERS_LABEL])
  })

  it('does not collapse anything when every holding is under the cutoff', () => {
    const rows = Array.from({ length: 40 }, (_, index) =>
      rowOf({ id: `small-${index}`, ticker: `SML${index}`, currentValue: 10 }),
    )

    const { entries } = computeAllocation(rows, 'currentValue')

    expect(entries).toHaveLength(40)
    expect(entries.some((entry) => entry.isOthers)).toBe(false)
  })

  it('treats a holding exactly at the cutoff as its own entry', () => {
    const rows = [
      rowOf({ id: 'big', ticker: 'BIGG3', currentValue: 9400 }),
      rowOf({ id: 'edge', ticker: 'EDGE3', currentValue: 300 }),
      rowOf({ id: 'small', ticker: 'SML1', currentValue: 150 }),
      rowOf({ id: 'small-2', ticker: 'SML2', currentValue: 150 }),
    ]

    const labels = computeAllocation(rows, 'currentValue').entries.map((entry) => entry.label)

    expect(labels).toContain('EDGE3')
    expect(labels).toContain(OTHERS_LABEL)
  })

  it('returns no entries for an empty wallet or when nothing has a value', () => {
    expect(computeAllocation([], 'currentValue').entries).toEqual([])
    const unpriced = computeAllocation([rowOf({ currentValue: null })], 'currentValue')
    expect(unpriced.entries).toEqual([])
    expect(unpriced.excludedCount).toBe(1)
  })
})
