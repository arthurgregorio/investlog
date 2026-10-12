import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi } from '@/api/auth'
import { router } from '@/router'
import { resetUserStores } from '@/stores/resetUserStores'
import type { SessionResponse, TotpEnrollResponse } from '@/types'

export type LoginStatus =
  | 'authenticated'
  | 'needs_enrollment'
  | 'totp_required'
  | 'invalid_totp_code'

async function enrollTotp(email: string, password: string): Promise<TotpEnrollResponse> {
  return authApi.enroll(email, password)
}

async function register(name: string, email: string, password: string): Promise<void> {
  await authApi.register(name, email, password)
}

export const useAuthStore = defineStore('auth', () => {
  const session = ref<SessionResponse | null>(null)
  const loading = ref(false)
  const endingSession = ref(false)
  const isAdmin = computed(() => session.value?.role === 'ADMIN')

  async function startSession(newSession: SessionResponse) {
    session.value = newSession
    await router.replace({ name: 'overview' })
  }

  async function endSession() {
    endingSession.value = true
    try {
      await router.replace({ name: 'login' })
    } finally {
      endingSession.value = false
      session.value = null
      resetUserStores()
    }
  }

  async function login(
    email: string,
    password: string,
    totpCode?: string,
    trustDevice?: boolean,
  ): Promise<LoginStatus> {
    const outcome = await authApi.login(email, password, totpCode, trustDevice)
    if (outcome.status === 'authenticated') {
      await startSession(outcome.session)
    }
    return outcome.status
  }

  async function verifyTotp(email: string, password: string, code: string): Promise<void> {
    await startSession(await authApi.verify(email, password, code))
  }

  async function linkGoogleAccount(linkToken: string, password: string): Promise<void> {
    await startSession(await authApi.linkGoogleAccount(linkToken, password))
  }

  async function logout() {
    await authApi.logout()
    await endSession()
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

  return {
    session,
    loading,
    endingSession,
    isAdmin,
    login,
    enrollTotp,
    verifyTotp,
    linkGoogleAccount,
    register,
    logout,
    restoreSession,
  }
})
