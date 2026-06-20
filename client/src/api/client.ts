import axios from 'axios'
import { ToastProgrammatic } from 'buefy'

const toast = new ToastProgrammatic()

export const apiClient = axios.create({
  baseURL: '/private/v1',
  headers: { 'Content-Type': 'application/json' },
})

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const message: string =
      error.response?.data?.detail ??
      error.response?.data?.message ??
      'Erro ao comunicar com o servidor.'
    toast.open({ message, type: 'is-danger', duration: 4000 })
    return Promise.reject(error)
  },
)
