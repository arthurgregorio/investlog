import { describe, expect, it } from 'vitest'
import { fmt } from './useFormat'

describe('fmt.money compact abbreviation', () => {
  it('formats values under 1,000 normally even when compact is requested', () => {
    expect(fmt.money(234.5, 'BRL', { compact: true })).toBe('R$ 234,50')
  })

  it('abbreviates values >= 1,000 to thousands with one decimal and a k suffix', () => {
    expect(fmt.money(1500, 'BRL', { compact: true })).toBe('R$ 1,5k')
  })

  it('abbreviates values >= 1,000,000 to one decimal with an M suffix', () => {
    expect(fmt.money(1234000, 'BRL', { compact: true })).toBe('R$ 1,2M')
  })

  it('does not abbreviate when compact is not requested', () => {
    expect(fmt.money(239000, 'BRL')).toBe('R$ 239.000,00')
  })
})
