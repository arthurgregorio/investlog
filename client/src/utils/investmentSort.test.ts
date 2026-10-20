import { describe, expect, it } from 'vitest'
import { DEFAULT_SORT, nextSort, sortParam } from './investmentSort'

describe('nextSort', () => {
  it('starts a new key ascending', () => {
    expect(nextSort({ key: 'gainPct', direction: 'desc' }, 'asset')).toEqual({
      key: 'asset',
      direction: 'asc',
    })
  })

  it('flips the direction when the same key is chosen again', () => {
    expect(nextSort({ key: 'price', direction: 'asc' }, 'price')).toEqual({
      key: 'price',
      direction: 'desc',
    })
    expect(nextSort({ key: 'price', direction: 'desc' }, 'price')).toEqual({
      key: 'price',
      direction: 'asc',
    })
  })

  it('returns null for an unknown key', () => {
    expect(nextSort({ key: 'gain', direction: 'asc' }, 'colour')).toBeNull()
  })
})

describe('sortParam', () => {
  it('joins the key and the direction the way GET /holdings expects', () => {
    expect(sortParam({ key: 'current', direction: 'asc' })).toBe('current,asc')
  })

  it('defaults to the gain percentage descending', () => {
    expect(sortParam(DEFAULT_SORT)).toBe('gainPct,desc')
  })
})
