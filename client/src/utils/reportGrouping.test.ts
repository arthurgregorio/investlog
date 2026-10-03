import { describe, expect, it } from 'vitest'
import { groupHoldingsForReport } from './reportGrouping'
import type { HoldingRow } from '@/types'

function holdingRow(overrides: Partial<HoldingRow>): HoldingRow {
  return {
    id: 'holding-1',
    kind: 'STOCKS',
    name: 'Petrobras',
    ticker: 'PETR4',
    typeLabel: 'Ação',
    walletId: 'wallet-1',
    walletName: 'Corretora A',
    walletCurrency: 'BRL',
    quantity: 10,
    costBasis: 1000,
    currentPrice: 120,
    currentValue: 1200,
    gain: 200,
    gainPct: 20,
    ...overrides,
  }
}

const keepAmount = (amount: number) => amount

describe('groupHoldingsForReport', () => {
  it('returns no groups and zeroed totals for an empty list', () => {
    const grouping = groupHoldingsForReport([], keepAmount)

    expect(grouping.kindGroups).toEqual([])
    expect(grouping.grandTotals).toEqual({
      costBasis: 0,
      currentValue: 0,
      gain: 0,
      gainPct: null,
    })
  })

  it('nests a single holding under its kind, type and wallet', () => {
    const grouping = groupHoldingsForReport([holdingRow({})], keepAmount)

    expect(grouping.kindGroups).toHaveLength(1)
    const [kindGroup] = grouping.kindGroups
    expect(kindGroup.kind).toBe('STOCKS')
    expect(kindGroup.label).toBe('Ações')
    expect(kindGroup.subGroups).toHaveLength(1)
    expect(kindGroup.subGroups[0].key).toBe('Ação')
    expect(kindGroup.subGroups[0].walletGroups).toHaveLength(1)
    expect(kindGroup.subGroups[0].walletGroups[0].walletName).toBe('Corretora A')
    expect(kindGroup.subGroups[0].walletGroups[0].rows).toHaveLength(1)
    expect(grouping.grandTotals).toEqual({
      costBasis: 1000,
      currentValue: 1200,
      gain: 200,
      gainPct: 20,
    })
  })

  it('splits holdings of the same type across wallets sorted by wallet name', () => {
    const grouping = groupHoldingsForReport(
      [
        holdingRow({ id: 'a', walletId: 'wallet-2', walletName: 'Zeta', costBasis: 500, currentValue: 400 }),
        holdingRow({ id: 'b', walletId: 'wallet-1', walletName: 'Alfa', costBasis: 1000, currentValue: 1500 }),
      ],
      keepAmount,
    )

    const [subGroup] = grouping.kindGroups[0].subGroups
    expect(subGroup.walletGroups.map((walletGroup) => walletGroup.walletName)).toEqual([
      'Alfa',
      'Zeta',
    ])
    expect(subGroup.walletGroups[0].totals.gain).toBe(500)
    expect(subGroup.walletGroups[1].totals.gain).toBe(-100)
    expect(subGroup.totals).toEqual({
      costBasis: 1500,
      currentValue: 1900,
      gain: 400,
      gainPct: (400 / 1500) * 100,
    })
  })

  it('orders rows inside a wallet by ticker', () => {
    const grouping = groupHoldingsForReport(
      [
        holdingRow({ id: 'a', ticker: 'VALE3', name: 'Vale' }),
        holdingRow({ id: 'b', ticker: 'ITUB4', name: 'Itaú' }),
      ],
      keepAmount,
    )

    const rows = grouping.kindGroups[0].subGroups[0].walletGroups[0].rows
    expect(rows.map((row) => row.holding.ticker)).toEqual(['ITUB4', 'VALE3'])
  })

  it('emits kind groups in the fixed stocks, crypto, funds order and sorts types alphabetically', () => {
    const grouping = groupHoldingsForReport(
      [
        holdingRow({ id: 'fund', kind: 'FUNDS', ticker: null, name: 'Fundo X', typeLabel: 'Renda Fixa' }),
        holdingRow({ id: 'crypto', kind: 'CRYPTO', ticker: 'BTC', name: 'Bitcoin', typeLabel: null }),
        holdingRow({ id: 'fii', typeLabel: 'FII', ticker: 'HGLG11', name: 'CSHG Logística' }),
        holdingRow({ id: 'stock', typeLabel: 'Ação' }),
      ],
      keepAmount,
    )

    expect(grouping.kindGroups.map((kindGroup) => kindGroup.label)).toEqual([
      'Ações',
      'Cripto',
      'Fundos',
    ])
    expect(grouping.kindGroups[0].subGroups.map((subGroup) => subGroup.label)).toEqual([
      'Ação',
      'FII',
    ])
  })

  it('groups crypto by ticker and falls back to the name when the ticker is missing', () => {
    const grouping = groupHoldingsForReport(
      [
        holdingRow({ id: 'a', kind: 'CRYPTO', ticker: 'BTC', name: 'Bitcoin', typeLabel: null }),
        holdingRow({ id: 'b', kind: 'CRYPTO', ticker: null, name: 'Token Raro', typeLabel: null }),
      ],
      keepAmount,
    )

    expect(grouping.kindGroups[0].subGroups.map((subGroup) => subGroup.key)).toEqual([
      'BTC',
      'Token Raro',
    ])
  })

  it('labels holdings without a type as Outros', () => {
    const grouping = groupHoldingsForReport([holdingRow({ typeLabel: null })], keepAmount)

    expect(grouping.kindGroups[0].subGroups[0].label).toBe('Outros')
  })

  it('treats a null current value as zero and a null current price as null', () => {
    const grouping = groupHoldingsForReport(
      [holdingRow({ costBasis: 800, currentValue: null, currentPrice: null })],
      keepAmount,
    )

    const [row] = grouping.kindGroups[0].subGroups[0].walletGroups[0].rows
    expect(row.currentValue).toBe(0)
    expect(row.currentPrice).toBeNull()
    expect(row.gain).toBe(-800)
    expect(row.gainPct).toBe(-100)
  })

  it('reports a null percentage when the cost basis is zero', () => {
    const grouping = groupHoldingsForReport(
      [holdingRow({ costBasis: 0, currentValue: 50 })],
      keepAmount,
    )

    const [row] = grouping.kindGroups[0].subGroups[0].walletGroups[0].rows
    expect(row.gainPct).toBeNull()
    expect(grouping.grandTotals.gainPct).toBeNull()
  })

  it('converts every monetary amount from the wallet currency', () => {
    const doubleUsd = (amount: number, fromCurrency: string) =>
      fromCurrency === 'USD' ? amount * 2 : amount

    const grouping = groupHoldingsForReport(
      [
        holdingRow({ id: 'a', walletCurrency: 'USD', costBasis: 100, currentValue: 150, currentPrice: 15 }),
        holdingRow({ id: 'b', walletCurrency: 'BRL', costBasis: 100, currentValue: 100, currentPrice: 10 }),
      ],
      doubleUsd,
    )

    const rows = grouping.kindGroups[0].subGroups[0].walletGroups[0].rows
    const convertedRow = rows.find((row) => row.holding.id === 'a')!
    expect(convertedRow.costBasis).toBe(200)
    expect(convertedRow.currentValue).toBe(300)
    expect(convertedRow.currentPrice).toBe(30)
    expect(grouping.grandTotals.costBasis).toBe(300)
    expect(grouping.grandTotals.currentValue).toBe(400)
  })
})
