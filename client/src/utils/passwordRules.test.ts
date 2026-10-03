import { describe, expect, it } from 'vitest'
import {
  meetsPasswordRequirements,
  PASSWORD_MAX_LENGTH,
  passwordRequirementStatus,
} from './passwordRules'

describe('passwordRequirementStatus', () => {
  it('flags every requirement as unmet for an empty password', () => {
    expect(passwordRequirementStatus('')).toEqual({
      minLength: false,
      hasUppercase: false,
      hasNumber: false,
    })
  })

  it('accepts exactly the minimum length', () => {
    expect(passwordRequirementStatus('abcdefgh').minLength).toBe(true)
    expect(passwordRequirementStatus('abcdefg').minLength).toBe(false)
  })

  it('rejects a password over the maximum length', () => {
    expect(passwordRequirementStatus('a'.repeat(PASSWORD_MAX_LENGTH)).minLength).toBe(true)
    expect(passwordRequirementStatus('a'.repeat(PASSWORD_MAX_LENGTH + 1)).minLength).toBe(false)
  })

  it('detects an uppercase letter and a digit independently', () => {
    expect(passwordRequirementStatus('Abc')).toMatchObject({ hasUppercase: true, hasNumber: false })
    expect(passwordRequirementStatus('abc1')).toMatchObject({ hasUppercase: false, hasNumber: true })
  })
})

describe('meetsPasswordRequirements', () => {
  it('passes when every requirement is met', () => {
    expect(meetsPasswordRequirements('Senha1234')).toBe(true)
  })

  it.each(['senha1234', 'SenhaSenha', 'Se1'])('fails for %s', (password) => {
    expect(meetsPasswordRequirements(password)).toBe(false)
  })
})
