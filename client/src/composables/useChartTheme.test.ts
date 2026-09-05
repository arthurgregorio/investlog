import { afterEach, describe, expect, it } from 'vitest'
import { donutCutoutPercent, hexToRgba, resolveColor } from './useChartTheme'

describe('resolveColor', () => {
  afterEach(() => {
    document.documentElement.style.removeProperty('--test-color')
  })

  it('resolves a var(--name) reference to its computed value', () => {
    document.documentElement.style.setProperty('--test-color', '#206bc4')
    expect(resolveColor('var(--test-color)')).toBe('#206bc4')
  })

  it('returns literal colors unchanged', () => {
    expect(resolveColor('#2fb344')).toBe('#2fb344')
  })

  it('returns non-var(), non-matching strings unchanged', () => {
    expect(resolveColor('rgba(0, 0, 0, 0.5)')).toBe('rgba(0, 0, 0, 0.5)')
  })
})

describe('hexToRgba', () => {
  it('converts a hex color and alpha into an rgba() string', () => {
    expect(hexToRgba('#206bc4', 0.22)).toBe('rgba(32, 107, 196, 0.22)')
  })

  it('handles hex colors without a leading #', () => {
    expect(hexToRgba('2fb344', 0)).toBe('rgba(47, 179, 68, 0)')
  })
})

describe('donutCutoutPercent', () => {
  it('matches DonutChart default props (size: 168, thickness: 22)', () => {
    expect(donutCutoutPercent(168, 22)).toBe(`${((168 / 2 - 22) / (168 / 2)) * 100}%`)
  })

  it('matches OverviewView usage (size: 156, thickness: 22)', () => {
    expect(donutCutoutPercent(156, 22)).toBe(`${((156 / 2 - 22) / (156 / 2)) * 100}%`)
  })
})
