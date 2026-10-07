import { describe, expect, it } from 'vitest'
import { badgeColor, WALLET_TYPES } from './walletTypes'
import type { WalletKind } from '@/types'

const KINDS: WalletKind[] = ['STOCKS', 'CRYPTO', 'FUNDS']

describe('WALLET_TYPES', () => {
  it.each(KINDS)('describes %s with a label, accent and icon', (kind) => {
    const meta = WALLET_TYPES[kind]

    expect(meta.label).not.toBe('')
    expect(meta.accent).toMatch(/^var\(--wt-/)
    expect(meta.icon).not.toBe('')
  })
})

describe('badgeColor', () => {
  it('returns the same color for the same ticker and kind', () => {
    expect(badgeColor('PETR4', 'STOCKS')).toBe(badgeColor('PETR4', 'STOCKS'))
  })

  it('returns an hsl color for a ticker', () => {
    expect(badgeColor('PETR4', 'STOCKS')).toMatch(/^hsl\(\d+, 55%, 38%\)$/)
  })

  it('differs across at least some tickers', () => {
    const colors = new Set(
      ['PETR4', 'VALE3', 'ITUB4', 'BBDC4', 'ABEV3', 'WEGE3'].map((ticker) =>
        badgeColor(ticker, 'STOCKS'),
      ),
    )

    expect(colors.size).toBeGreaterThan(1)
  })

  it('shifts the hue by kind for the same ticker', () => {
    const colors = new Set(KINDS.map((kind) => badgeColor('ABC', kind)))

    expect(colors.size).toBe(3)
  })

  it.each([null, undefined, ''])('falls back to the neutral color for %j on stocks', (ticker) => {
    expect(badgeColor(ticker, 'STOCKS')).toBe('var(--ticker-fallback)')
    expect(badgeColor(ticker, 'CRYPTO')).toBe('var(--ticker-fallback)')
  })

  it.each([null, undefined])('falls back to the funds color for %j on funds', (ticker) => {
    expect(badgeColor(ticker, 'FUNDS')).toBe('var(--ticker-fallback-funds)')
  })
})
