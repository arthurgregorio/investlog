import { describe, expect, it } from 'vitest'
import { useExpandedRow } from './useExpandedRow'

describe('useExpandedRow', () => {
  it('starts with every row collapsed', () => {
    const { expandedId, isExpanded } = useExpandedRow()

    expect(expandedId.value).toBeNull()
    expect(isExpanded('holding-1')).toBe(false)
  })

  it('expands a row and collapses it again on a second toggle', () => {
    const { isExpanded, toggle } = useExpandedRow()

    toggle('holding-1')
    expect(isExpanded('holding-1')).toBe(true)

    toggle('holding-1')
    expect(isExpanded('holding-1')).toBe(false)
  })

  it('keeps a single row expanded at a time', () => {
    const { isExpanded, toggle } = useExpandedRow()

    toggle('holding-1')
    toggle('holding-2')

    expect(isExpanded('holding-1')).toBe(false)
    expect(isExpanded('holding-2')).toBe(true)
  })

  it('collapses whatever row is expanded', () => {
    const { expandedId, toggle, collapse } = useExpandedRow()

    toggle('holding-1')
    collapse()

    expect(expandedId.value).toBeNull()
  })
})
