import { fmt } from '@/composables/useFormat'
import type { StrategyClass } from '@/types'

export type PercentageDraft = number | ''

const HUNDRED = 100
const TOLERANCE = 0.005

export function typedSum(targets: PercentageDraft[]): number {
  return targets.reduce<number>((sum, target) => sum + (target === '' ? 0 : Number(target)), 0)
}

export function blankShare(targets: PercentageDraft[]): number | null {
  const blankCount = targets.filter((target) => target === '').length
  if (blankCount === 0) return null
  return Math.max(HUNDRED - typedSum(targets), 0) / blankCount
}

export function isHundred(value: number): boolean {
  return Math.abs(value - HUNDRED) < TOLERANCE
}

export function holdingTargetsValid(targets: PercentageDraft[]): boolean {
  const sum = typedSum(targets)
  if (sum > HUNDRED + TOLERANCE) return false
  return targets.some((target) => target === '') || isHundred(sum)
}

export function classTargetSummary(strategyClass: StrategyClass): string {
  const { holdingCount, typedCount, typedPercentageSum, blankSharePercentage } = strategyClass
  if (holdingCount === 0) return 'Nenhum investimento'
  if (typedCount === holdingCount) return 'Todos com meta'
  const share = fmt.pct(blankSharePercentage ?? 0)
  if (typedCount === 0) return `Todos dividem igualmente · ${share} cada`
  const blankCount = holdingCount - typedCount
  const remaining = fmt.pct(Math.max(HUNDRED - typedPercentageSum, 0))
  return `${typedCount} com meta · ${blankCount} dividem ${remaining} (${share} cada)`
}

export function sumGapLabel(sum: number): string {
  if (isHundred(sum)) return `Soma das metas: ${fmt.pct(sum)}`
  const gap = `${fmt.decimal(HUNDRED - sum)} pp`
  return sum < HUNDRED
    ? `Soma das metas: ${fmt.pct(sum)} · faltam ${gap}`
    : `Soma das metas: ${fmt.pct(sum)} · passa ${gap}`
}
