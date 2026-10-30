import { fmt } from '@/composables/useFormat'
import type { HoldingAllocation, LeftOutReason } from '@/types'

const MINUS = '−'

export function driftLabel(percentagePoints: number): string {
  const rounded = Math.round(percentagePoints * 10) / 10
  const text = Math.abs(rounded).toFixed(1).replace('.', ',')
  if (rounded > 0) return `+${text} pp`
  if (rounded < 0) return `${MINUS}${text} pp`
  return `${text} pp`
}

export function driftTagType(percentagePoints: number): string {
  const rounded = Math.round(percentagePoints * 10) / 10
  if (rounded > 0) return 'is-warning is-light'
  if (rounded < 0) return 'is-info is-light'
  return 'is-success is-light'
}

export function moneyToTarget(driftValue: number, currency: string): string {
  if (Math.abs(driftValue) < 0.005) return 'na meta'
  const amount = fmt.money(Math.abs(driftValue), currency)
  return driftValue > 0 ? `${amount} acima` : `${amount} abaixo`
}

export function targetLabel(percentage: number): string {
  return Number.isInteger(percentage) ? `${percentage}%` : fmt.pct(percentage)
}

export function holdingCountLabel(count: number, noun = 'investimento'): string {
  return count === 1 ? `1 ${noun}` : `${count} ${noun}s`
}

export const LEFT_OUT_REASONS: Record<LeftOutReason, { label: string; type: string }> = {
  FROZEN: { label: 'congelado', type: 'is-info is-light' },
  ABOVE_TARGET_IN_CLASS: { label: 'acima da meta na classe', type: 'is-warning is-light' },
  CLASS_ABOVE_TARGET: { label: 'classe acima da meta', type: 'is-warning is-light' },
}

export interface PlanHoldingRow {
  id: string
  className: string
  holding: HoldingAllocation
  amount?: number
  reason?: LeftOutReason
}
