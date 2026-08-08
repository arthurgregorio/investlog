import axios from 'axios'
import { ToastProgrammatic } from 'buefy'
import { router } from '@/router'

const toast = new ToastProgrammatic()

export const apiClient = axios.create({
  baseURL: '/private/v1',
  headers: { 'Content-Type': 'application/json' },
})

const SILENT_ERROR_CODES = new Set(['totp_required', 'invalid_totp_code', 'invalid_credentials'])

// 401s from these endpoints are user-facing form outcomes (wrong password/code) that the
// calling view already reports inline — not a dead/expired session, so they're never silenced
// by URL alone here and fall through to SILENT_ERROR_CODES / the toast below as before.
const AUTH_FORM_ENDPOINTS = [
  '/auth/login',
  '/auth/totp/enroll',
  '/auth/totp/verify',
  '/profile/password',
]

function isAuthFormRequest(url: string | undefined): boolean {
  return url !== undefined && AUTH_FORM_ENDPOINTS.some((endpoint) => url.startsWith(endpoint))
}

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && !isAuthFormRequest(error.config?.url)) {
      if (router.currentRoute.value.name !== 'login') {
        router.push({ name: 'login' })
      }
      return Promise.reject(error)
    }
    if (SILENT_ERROR_CODES.has(error.response?.data?.error)) {
      return Promise.reject(error)
    }
    const message: string =
      error.response?.data?.detail ??
      error.response?.data?.message ??
      'Erro ao comunicar com o servidor.'
    toast.open({ message, type: 'is-danger', duration: 4000 })
    return Promise.reject(error)
  },
)
