import { describe, expect, it } from 'vitest'
import { walletKindRows } from './walletKindRows'
import type { KindSummary } from '@/types'

const stocksSummary: KindSummary = {
  kind: 'STOCKS',
  holdingCount: 3,
  totalCostBasis: 6000,
  totalCurrentValue: 7500,
  totalGain: 1500,
  totalGainPct: 25,
}

describe('walletKindRows', () => {
  it('returns one row per wallet kind in a fixed order', () => {
    expect(walletKindRows([], []).map((row) => row.key)).toEqual(['STOCKS', 'CRYPTO', 'FUNDS'])
  })

  it('fills a kind from its summary and counts its wallets', () => {
    const [stocks] = walletKindRows(
      [stocksSummary],
      [{ kind: 'STOCKS' }, { kind: 'STOCKS' }, { kind: 'CRYPTO' }],
    )

    expect(stocks).toEqual({
      key: 'STOCKS',
      label: 'Ações',
      accent: 'var(--wt-stocks)',
      icon: 'trending-up',
      invested: 6000,
      currentValue: 7500,
      gain: 1500,
      gainPct: 25,
      walletCount: 2,
      holdings: 3,
    })
  })

  it('zeroes a kind with no summary and leaves its gain empty', () => {
    const funds = walletKindRows([stocksSummary], [])[2]

    expect(funds).toMatchObject({
      invested: 0,
      currentValue: 0,
      gain: null,
      gainPct: null,
      walletCount: 0,
      holdings: 0,
    })
  })
})
