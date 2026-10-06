import { expect } from 'vitest'

export function expectDialogShowsLiterally(payload: string) {
  const dialogElement = document.body.querySelector('.dialog')!
  expect(dialogElement.textContent).toContain(payload)
  expect(dialogElement.querySelector('img')).toBeNull()
}
