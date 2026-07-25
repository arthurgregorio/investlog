/* Appearance preferences — theme (dark) + accent color.
   Preferences (not logbook data) persist to localStorage. */
import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import type { AccentKey } from '@/types'

const STORAGE_KEY = 'investlog.appearance'
const VALID_ACCENTS: AccentKey[] = ['blue', 'indigo', 'teal', 'yellow']

interface Persisted {
  dark: boolean
  accent: AccentKey
}

function load(): Persisted {
  const fallback: Persisted = { dark: false, accent: 'teal' } // teal is the design default
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return fallback
    const merged = { ...fallback, ...(JSON.parse(raw) as Partial<Persisted>) }
    if (!VALID_ACCENTS.includes(merged.accent)) {
      merged.accent = fallback.accent
    }
    return merged
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
        localStorage.setItem(
          STORAGE_KEY,
          JSON.stringify({ dark: dark.value, accent: accent.value }),
        )
      } catch {
        /* ignore quota / privacy-mode errors */
      }
    },
    { flush: 'post' },
  )

  return { dark, accent, toggleDark, setAccent }
})
