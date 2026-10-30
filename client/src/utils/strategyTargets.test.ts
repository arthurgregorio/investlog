import { describe, expect, it } from 'vitest'
import {
  blankShare,
  classTargetSummary,
  holdingTargetsValid,
  isHundred,
  sumGapLabel,
  typedSum,
} from './strategyTargets'
import { strategyClass } from '@/test/strategyFixtures'

describe('strategyTargets', () => {
  it('sums the typed targets and splits what is left among the blanks', () => {
    expect(typedSum([15, '', 15, ''])).toBe(30)
    expect(blankShare([15, '', 15, ''])).toBe(35)
    expect(blankShare([60, 50, ''])).toBe(0)
    expect(blankShare([50, 50])).toBeNull()
  })

  it('accepts typed targets up to 100 and a fully typed class only at exactly 100', () => {
    expect(holdingTargetsValid([40, ''])).toBe(true)
    expect(holdingTargetsValid([100, ''])).toBe(true)
    expect(holdingTargetsValid([60, 50, ''])).toBe(false)
    expect(holdingTargetsValid([60, 40])).toBe(true)
    expect(holdingTargetsValid([60, 30])).toBe(false)
    expect(holdingTargetsValid([])).toBe(false)
    expect(isHundred(99.999)).toBe(true)
  })

  it('summarises how a class splits its holdings', () => {
    expect(classTargetSummary(strategyClass({ holdingCount: 0 }))).toBe('Nenhum investimento')
    expect(classTargetSummary(strategyClass({ holdingCount: 2, typedCount: 2 }))).toBe(
      'Todos com meta',
    )
    expect(
      classTargetSummary(
        strategyClass({ holdingCount: 3, typedCount: 0, blankSharePercentage: 33.3333 }),
      ),
    ).toBe('Todos dividem igualmente · 33,33% cada')
    expect(
      classTargetSummary(
        strategyClass({
          holdingCount: 10,
          typedCount: 3,
          typedPercentageSum: 45,
          blankSharePercentage: 7.8571,
        }),
      ),
    ).toBe('3 com meta · 7 dividem 55,00% (7,86% cada)')
    expect(
      classTargetSummary(
        strategyClass({
          holdingCount: 2,
          typedCount: 1,
          typedPercentageSum: 100,
          blankSharePercentage: null,
        }),
      ),
    ).toBe('1 com meta · 1 dividem 0,00% (0,00% cada)')
  })

  it('labels the class sum with what is missing or over', () => {
    expect(sumGapLabel(100)).toBe('Soma das metas: 100,00%')
    expect(sumGapLabel(95)).toBe('Soma das metas: 95,00% · faltam 5,00 pp')
    expect(sumGapLabel(110)).toBe('Soma das metas: 110,00% · passa 10,00 pp')
  })
})
