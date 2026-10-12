import { describe, expect, it } from 'vitest'
import { holdingTypeLabel } from './holdingLabels'

describe('holdingTypeLabel', () => {
  it('uses the asset type when there is one', () => {
    expect(holdingTypeLabel({ kind: 'STOCKS', typeLabel: 'FII' })).toBe('FII')
    expect(holdingTypeLabel({ kind: 'FUNDS', typeLabel: 'Renda Fixa' })).toBe('Renda Fixa')
  })

  it('falls back to the kind when the type is missing', () => {
    expect(holdingTypeLabel({ kind: 'STOCKS', typeLabel: null })).toBe('Ação')
    expect(holdingTypeLabel({ kind: 'FUNDS', typeLabel: null })).toBe('Fundo')
  })

  it('always labels crypto as Cripto', () => {
    expect(holdingTypeLabel({ kind: 'CRYPTO', typeLabel: 'Ignorado' })).toBe('Cripto')
  })
})
