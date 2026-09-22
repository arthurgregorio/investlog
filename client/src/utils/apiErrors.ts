import { isAxiosError } from 'axios'

export function fieldValidationMessage(error: unknown): string | undefined {
  if (!isAxiosError(error) || error.response?.status !== 400) return undefined
  const errors = error.response.data?.errors
  if (!Array.isArray(errors) || errors.length === 0) return undefined
  return errors.join('. ')
}

// A 400 arrives in two shapes: @Valid failures carry an `errors` array, while a rejected domain
// rule carries only `detail`. Endpoints that show the message inline need both.
export function problemDetailMessage(error: unknown): string | undefined {
  const fieldMessage = fieldValidationMessage(error)
  if (fieldMessage) return fieldMessage
  if (!isAxiosError(error) || error.response?.status !== 400) return undefined
  const detail = error.response.data?.detail
  return typeof detail === 'string' && detail.length > 0 ? detail : undefined
}
