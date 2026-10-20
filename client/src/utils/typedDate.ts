const DIGITS_ONLY = /^(\d{2})(\d{2})(\d{4})$/
const SLASHED = /^(\d{2})\/(\d{2})\/(\d{4})$/

export function parseTypedDate(text: string): Date | null {
  const trimmed = text.trim()
  const match = DIGITS_ONLY.exec(trimmed) ?? SLASHED.exec(trimmed)
  if (!match) return null

  const [, day, month, year] = match.map(Number)
  const date = new Date(year, month - 1, day)
  const readsBackUnchanged =
    date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day
  return readsBackUnchanged ? date : null
}
