import { describe, expect, it } from 'vitest'
import {
  driftLabel,
  driftTagType,
  holdingCountLabel,
  LEFT_OUT_REASONS,
  moneyToTarget,
  targetLabel,
} from './allocationLabels'

describe('allocationLabels', () => {
  it('labels a drift in percentage points with its sign and tag colour', () => {
    expect(driftLabel(3.26)).toBe('+3,3 pp')
    expect(driftLabel(-3.06)).toBe('−3,1 pp')
    expect(driftLabel(0.02)).toBe('0,0 pp')
    expect(driftTagType(3.26)).toBe('is-warning is-light')
    expect(driftTagType(-3.06)).toBe('is-info is-light')
    expect(driftTagType(-0.04)).toBe('is-success is-light')
  })

  it('says how much money is above or below the target', () => {
    expect(moneyToTarget(22249.63, 'BRL')).toBe('R$ 22.249,63 acima')
    expect(moneyToTarget(-20705.4, 'BRL')).toBe('R$ 20.705,40 abaixo')
    expect(moneyToTarget(0.001, 'BRL')).toBe('na meta')
  })

  it('formats targets and counts', () => {
    expect(targetLabel(22)).toBe('22%')
    expect(targetLabel(22.5)).toBe('22,50%')
    expect(holdingCountLabel(1)).toBe('1 investimento')
    expect(holdingCountLabel(3, 'ativo')).toBe('3 ativos')
    expect(LEFT_OUT_REASONS.FROZEN.label).toBe('congelado')
  })
})
