import { describe, expect, it } from 'vitest'
import { parseTypedDate } from './typedDate'

describe('parseTypedDate', () => {
  it('reads eight digits as DDMMYYYY', () => {
    expect(parseTypedDate('20012026')).toEqual(new Date(2026, 0, 20))
  })

  it('keeps reading the slashed DD/MM/YYYY form', () => {
    expect(parseTypedDate('20/01/2026')).toEqual(new Date(2026, 0, 20))
  })

  it('reads leading zeros in the day and month', () => {
    expect(parseTypedDate('01012026')).toEqual(new Date(2026, 0, 1))
  })

  it('ignores surrounding whitespace', () => {
    expect(parseTypedDate('  20012026 ')).toEqual(new Date(2026, 0, 20))
  })

  it('builds the date at local midnight', () => {
    const date = parseTypedDate('20012026')

    expect([date?.getHours(), date?.getMinutes(), date?.getSeconds()]).toEqual([0, 0, 0])
  })

  it('rejects a day the month does not have instead of rolling it over', () => {
    expect(parseTypedDate('31022026')).toBeNull()
    expect(parseTypedDate('32012026')).toBeNull()
    expect(parseTypedDate('20132026')).toBeNull()
    expect(parseTypedDate('00012026')).toBeNull()
  })

  it('accepts 29 February only in a leap year', () => {
    expect(parseTypedDate('29022027')).toBeNull()
    expect(parseTypedDate('29022028')).toEqual(new Date(2028, 1, 29))
  })

  it('rejects seven or nine digits', () => {
    expect(parseTypedDate('2001202')).toBeNull()
    expect(parseTypedDate('200120260')).toBeNull()
  })

  it('rejects text with letters', () => {
    expect(parseTypedDate('20jan2026')).toBeNull()
    expect(parseTypedDate('abcdefgh')).toBeNull()
  })

  it('rejects empty text', () => {
    expect(parseTypedDate('')).toBeNull()
  })
})
