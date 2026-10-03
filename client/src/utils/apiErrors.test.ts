import { AxiosError, type AxiosResponse } from 'axios'
import { describe, expect, it } from 'vitest'
import { fieldValidationMessage, problemDetailMessage } from './apiErrors'

function axiosErrorWith(status: number, data: unknown): AxiosError {
  const error = new AxiosError('Request failed')
  error.response = { status, data } as AxiosResponse
  return error
}

describe('fieldValidationMessage', () => {
  it('joins the validation errors of a 400', () => {
    const error = axiosErrorWith(400, { errors: ['Nome é obrigatório', 'Valor inválido'] })

    expect(fieldValidationMessage(error)).toBe('Nome é obrigatório. Valor inválido')
  })

  it('ignores a non-axios error', () => {
    expect(fieldValidationMessage(new Error('boom'))).toBeUndefined()
  })

  it('ignores a response that is not a 400', () => {
    expect(fieldValidationMessage(axiosErrorWith(500, { errors: ['x'] }))).toBeUndefined()
  })

  it('ignores a 400 without an errors array', () => {
    expect(fieldValidationMessage(axiosErrorWith(400, { detail: 'x' }))).toBeUndefined()
  })

  it('ignores a 400 with an empty errors array', () => {
    expect(fieldValidationMessage(axiosErrorWith(400, { errors: [] }))).toBeUndefined()
  })
})

describe('problemDetailMessage', () => {
  it('prefers the field validation errors', () => {
    const error = axiosErrorWith(400, { errors: ['Campo inválido'], detail: 'Regra violada' })

    expect(problemDetailMessage(error)).toBe('Campo inválido')
  })

  it('falls back to the detail of a rejected domain rule', () => {
    expect(problemDetailMessage(axiosErrorWith(400, { detail: 'Saldo insuficiente' }))).toBe(
      'Saldo insuficiente',
    )
  })

  it('ignores an empty detail', () => {
    expect(problemDetailMessage(axiosErrorWith(400, { detail: '' }))).toBeUndefined()
  })

  it('ignores a detail that is not a string', () => {
    expect(problemDetailMessage(axiosErrorWith(400, { detail: 42 }))).toBeUndefined()
  })

  it('ignores a non-400 response', () => {
    expect(problemDetailMessage(axiosErrorWith(404, { detail: 'x' }))).toBeUndefined()
  })

  it('ignores a non-axios error', () => {
    expect(problemDetailMessage('boom')).toBeUndefined()
  })
})
