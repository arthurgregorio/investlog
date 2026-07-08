import { defineStore } from 'pinia'
import { ref } from 'vue'
import { authApi } from '@/api/auth'
import type { SessionResponse, TotpEnrollResponse } from '@/types'

export type LoginStatus = 'authenticated' | 'needs_enrollment' | 'totp_required' | 'invalid_totp_code'

export const useAuthStore = defineStore('auth', () => {
  const session = ref<SessionResponse | null>(null)
  const loading = ref(false)

  async function login(email: string, password: string, totpCode?: string): Promise<LoginStatus> {
    const outcome = await authApi.login(email, password, totpCode)
    if (outcome.status === 'authenticated') {
      session.value = outcome.session
    }
    return outcome.status
  }

  async function enrollTotp(email: string, password: string): Promise<TotpEnrollResponse> {
    return authApi.enroll(email, password)
  }

  async function verifyTotp(email: string, password: string, code: string): Promise<void> {
    session.value = await authApi.verify(email, password, code)
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

  return { session, loading, login, enrollTotp, verifyTotp, logout, restoreSession }
})
