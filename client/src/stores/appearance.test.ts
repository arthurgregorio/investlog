import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { useAppearanceStore } from './appearance'

const STORAGE_KEY = 'investlog.appearance'

describe('appearance store', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('starts with the light theme and the teal accent on a clean start', () => {
    const store = useAppearanceStore()

    expect(store.dark).toBe(false)
    expect(store.accent).toBe('teal')
  })

  it('falls back to the defaults when the stored payload is malformed', () => {
    localStorage.setItem(STORAGE_KEY, '{not json')

    const store = useAppearanceStore()

    expect(store.dark).toBe(false)
    expect(store.accent).toBe('teal')
  })

  it('restores a persisted theme and accent', () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ dark: true, accent: 'indigo' }))

    const store = useAppearanceStore()

    expect(store.dark).toBe(true)
    expect(store.accent).toBe('indigo')
  })

  it('keeps the default for a field missing from the persisted payload', () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ dark: true }))

    const store = useAppearanceStore()

    expect(store.dark).toBe(true)
    expect(store.accent).toBe('teal')
  })

  it('replaces an unknown persisted accent with the default', () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ dark: true, accent: 'magenta' }))

    const store = useAppearanceStore()

    expect(store.dark).toBe(true)
    expect(store.accent).toBe('teal')
  })

  it('toggleDark flips the theme and persists it', async () => {
    const store = useAppearanceStore()

    store.toggleDark()
    await nextTick()

    expect(store.dark).toBe(true)
    expect(JSON.parse(localStorage.getItem(STORAGE_KEY)!)).toEqual({ dark: true, accent: 'teal' })

    store.toggleDark()
    await nextTick()

    expect(store.dark).toBe(false)
    expect(JSON.parse(localStorage.getItem(STORAGE_KEY)!)).toEqual({ dark: false, accent: 'teal' })
  })

  it('setAccent changes the accent and persists it', async () => {
    const store = useAppearanceStore()

    store.setAccent('yellow')
    await nextTick()

    expect(store.accent).toBe('yellow')
    expect(JSON.parse(localStorage.getItem(STORAGE_KEY)!)).toEqual({
      dark: false,
      accent: 'yellow',
    })
  })

  it('keeps working when the storage rejects a write', async () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('quota exceeded', 'QuotaExceededError')
    })
    const store = useAppearanceStore()

    store.toggleDark()
    await nextTick()

    expect(store.dark).toBe(true)
  })
})
