import { describe, expect, it } from 'vitest'
import {
  compareDisplayNames,
  holdingDisplayName,
  sortHoldingsByDisplayName,
} from './sortHoldingsByDisplayName'
import type { HoldingRow } from '@/types'

function holdingOf(id: string, ticker: string | null, name: string): HoldingRow {
  return {
    id,
    kind: ticker ? 'STOCKS' : 'FUNDS',
    name,
    ticker,
    typeLabel: null,
    segmentLabel: null,
    walletId: 'wallet-stocks',
    walletName: 'Ações',
    walletCurrency: 'BRL',
    quantity: null,
    costBasis: 0,
    currentPrice: null,
    currentValue: null,
    gain: null,
    gainPct: null,
    frozen: false,
  }
}

describe('holdingDisplayName', () => {
  it('names a holding by its ticker', () => {
    expect(holdingDisplayName(holdingOf('holding-petr4', 'PETR4', 'Petrobras'))).toBe('PETR4')
  })

  it('falls back to the name when there is no ticker', () => {
    expect(holdingDisplayName(holdingOf('holding-selic', null, 'Tesouro Selic'))).toBe(
      'Tesouro Selic',
    )
  })
})

describe('compareDisplayNames', () => {
  it('ignores case', () => {
    expect(compareDisplayNames('petr4', 'PETR4')).toBe(0)
  })

  it('compares numeric suffixes as numbers', () => {
    expect(compareDisplayNames('ITSA4', 'ITSA10')).toBeLessThan(0)
  })

  it('ignores accents', () => {
    expect(compareDisplayNames('Ações', 'acoes')).toBe(0)
  })
})

describe('sortHoldingsByDisplayName', () => {
  const itausa10 = holdingOf('holding-itsa10', 'ITSA10', 'Itaúsa')
  const itausa4 = holdingOf('holding-itsa4', 'ITSA4', 'Itaúsa')
  const lowercaseBank = holdingOf('holding-bbas3', 'bbas3', 'Banco do Brasil')
  const treasury = holdingOf('holding-selic', null, 'Tesouro Selic')
  const vale = holdingOf('holding-vale3', 'VALE3', 'Vale')

  it('orders by ticker, or name without one, A to Z with case ignored and numbers compared naturally', () => {
    const sorted = sortHoldingsByDisplayName([vale, itausa10, treasury, itausa4, lowercaseBank])

    expect(sorted.map((holding) => holding.id)).toEqual([
      'holding-bbas3',
      'holding-itsa4',
      'holding-itsa10',
      'holding-selic',
      'holding-vale3',
    ])
  })

  it('leaves the given list untouched', () => {
    const holdings = [vale, itausa4]

    sortHoldingsByDisplayName(holdings)

    expect(holdings).toEqual([vale, itausa4])
  })
})
