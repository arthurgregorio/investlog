import axios from 'axios'
import { ToastProgrammatic } from 'buefy'
import { router } from '@/router'

const toast = new ToastProgrammatic()

export const apiClient = axios.create({
  baseURL: '/private/v1',
  headers: { 'Content-Type': 'application/json' },
})

const SILENT_ERROR_CODES = ['totp_required', 'invalid_totp_code']

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && router.currentRoute.value.name !== 'login') {
      router.push({ name: 'login' })
      return Promise.reject(error)
    }
    if (SILENT_ERROR_CODES.includes(error.response?.data?.error)) {
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
