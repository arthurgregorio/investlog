import { describe, expect, it } from 'vitest'
import { fmt } from './useFormat'

describe('fmt.sym', () => {
  it('maps known currencies to their symbol', () => {
    expect(fmt.sym('BRL')).toBe('R$')
    expect(fmt.sym('USD')).toBe('US$')
    expect(fmt.sym('EUR')).toBe('€')
  })

  it('defaults to BRL and falls back to the code for an unknown currency', () => {
    expect(fmt.sym()).toBe('R$')
    expect(fmt.sym('JPY')).toBe('JPY ')
  })
})

describe('fmt.money', () => {
  it('formats positive, negative and zero values with two decimals', () => {
    expect(fmt.money(1234.5)).toBe('R$ 1.234,50')
    expect(fmt.money(-1234.5)).toBe('R$ -1.234,50')
    expect(fmt.money(0)).toBe('R$ 0,00')
  })

  it('abbreviates negative values by magnitude when compact', () => {
    expect(fmt.money(-2500, 'BRL', { compact: true })).toBe('R$ -2,5k')
    expect(fmt.money(-3_400_000, 'USD', { compact: true })).toBe('US$ -3,4M')
  })
})

describe('fmt.moneySigned', () => {
  it('prefixes a plus for positive values and zero', () => {
    expect(fmt.moneySigned(10)).toBe('+R$ 10,00')
    expect(fmt.moneySigned(0)).toBe('+R$ 0,00')
  })

  it('prefixes a minus sign for negative values and drops the raw sign', () => {
    expect(fmt.moneySigned(-10)).toBe('−R$ 10,00')
  })

  it('forwards the currency and compact option', () => {
    expect(fmt.moneySigned(-2500, 'USD', { compact: true })).toBe('−US$ 2,5k')
  })
})

describe('fmt.pct and fmt.pctSigned', () => {
  it('formats the magnitude with two decimals', () => {
    expect(fmt.pct(12.345)).toBe('12,35%')
    expect(fmt.pct(-12.3)).toBe('12,30%')
    expect(fmt.pct(0)).toBe('0,00%')
  })

  it('signs positive, negative and zero values', () => {
    expect(fmt.pctSigned(5)).toBe('+5,00%')
    expect(fmt.pctSigned(-5)).toBe('−5,00%')
    expect(fmt.pctSigned(0)).toBe('+0,00%')
  })
})

describe('fmt.qty', () => {
  it('prints integers without decimals', () => {
    expect(fmt.qty(100)).toBe('100')
    expect(fmt.qty(0)).toBe('0')
    expect(fmt.qty(-3)).toBe('-3')
  })

  it('prints fractions with up to eight decimals in pt-BR', () => {
    expect(fmt.qty(0.5)).toBe('0,5')
    expect(fmt.qty(0.123456789)).toBe('0,12345679')
    expect(fmt.qty(-1.25)).toBe('-1,25')
  })
})

describe('fmt.date and fmt.dateShort', () => {
  it('formats an ISO date with the day, month abbreviation and year', () => {
    expect(fmt.date('2026-03-07')).toBe('7 mar 2026')
    expect(fmt.date('2025-12-31')).toBe('31 dez 2025')
  })

  it('formats an ISO date as month abbreviation and two-digit year', () => {
    expect(fmt.dateShort('2026-03-07')).toBe('mar/26')
    expect(fmt.dateShort('2025-12-31')).toBe('dez/25')
  })
})

describe('fmt.money compact abbreviation', () => {
  it('formats values under 1,000 normally even when compact is requested', () => {
    expect(fmt.money(234.5, 'BRL', { compact: true })).toBe('R$ 234,50')
  })

  it('abbreviates values >= 1,000 to thousands with one decimal and a k suffix', () => {
    expect(fmt.money(1234.5, 'BRL', { compact: true })).toBe('R$ 1,2k')
  })

  it('abbreviates values >= 1,000,000 to one decimal with an M suffix', () => {
    expect(fmt.money(1234000, 'BRL', { compact: true })).toBe('R$ 1,2M')
  })

  it('does not abbreviate when compact is not requested', () => {
    expect(fmt.money(239000, 'BRL')).toBe('R$ 239.000,00')
  })
})
