import { isAxiosError } from 'axios'

export function fieldValidationMessage(error: unknown): string | undefined {
  if (!isAxiosError(error) || error.response?.status !== 400) return undefined
  const errors = error.response.data?.errors
  if (!Array.isArray(errors) || errors.length === 0) return undefined
  return errors.join('. ')
}
