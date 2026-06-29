import { defineStore } from 'pinia'
import { ref } from 'vue'
import { authApi } from '@/api/auth'
import type { SessionResponse } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  const session = ref<SessionResponse | null>(null)
  const loading = ref(false)

  async function login(email: string, password: string) {
    session.value = await authApi.login(email, password)
  }

  async function logout() {
    await authApi.logout()
    session.value = null
  }

  async function restoreSession() {
    loading.value = true
    try {
      session.value = await authApi.fetchSession()
    } catch {
      session.value = null
    } finally {
      loading.value = false
    }
  }

  return { session, loading, login, logout, restoreSession }
})
