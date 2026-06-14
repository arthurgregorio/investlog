/* Appearance preferences — theme (dark) + accent color.
   Preferences (not logbook data) persist to localStorage. */
import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import type { AccentKey } from '@/types'

const STORAGE_KEY = 'investlog.appearance'

interface Persisted {
  dark: boolean
  accent: AccentKey
}

function load(): Persisted {
  const fallback: Persisted = { dark: false, accent: 'teal' } // teal is the design default
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return fallback
    return { ...fallback, ...(JSON.parse(raw) as Partial<Persisted>) }
  } catch {
    return fallback
  }
}

export const useAppearanceStore = defineStore('appearance', () => {
  const initial = load()
  const dark = ref<boolean>(initial.dark)
  const accent = ref<AccentKey>(initial.accent)

  function toggleDark() {
    dark.value = !dark.value
  }
  function setAccent(value: AccentKey) {
    accent.value = value
  }

  watch(
    [dark, accent],
    () => {
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify({ dark: dark.value, accent: accent.value }))
      } catch {
        /* ignore quota / privacy-mode errors */
      }
    },
    { flush: 'post' },
  )

  return { dark, accent, toggleDark, setAccent }
})
