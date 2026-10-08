import { describe, expect, it } from 'vitest'
import { statusTagType } from './userStatus'

describe('statusTagType', () => {
  it('maps every user status to its tag colour', () => {
    expect(statusTagType).toEqual({
      APPROVED: 'is-success',
      BLOCKED: 'is-danger',
      PENDING: 'is-warning',
    })
  })
})
