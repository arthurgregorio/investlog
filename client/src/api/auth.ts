import { apiClient } from './client'
import type { SessionResponse } from '@/types'

export const authApi = {
  login(email: string, password: string): Promise<SessionResponse> {
    return apiClient.post<SessionResponse>('/auth/login', { email, password }).then((response) => response.data)
  },
  logout(): Promise<void> {
    return apiClient.post('/auth/logout').then(() => undefined)
  },
  fetchSession(): Promise<SessionResponse> {
    return apiClient.get<SessionResponse>('/auth/session').then((response) => response.data)
  },
}
