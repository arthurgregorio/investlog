import { isAxiosError } from 'axios'
import { apiClient } from './client'
import type { AuthConfigResponse, LoginOutcome, SessionResponse, TotpEnrollResponse, TrustedDeviceResponse } from '@/types'

export const authApi = {
  async login(
    email: string,
    password: string,
    totpCode?: string,
    trustDevice?: boolean,
  ): Promise<LoginOutcome> {
    try {
      const response = await apiClient.post<SessionResponse>('/auth/login', {
        email,
        password,
        totpCode,
        trustDevice,
      })
      if (response.status === 202) {
        return { status: 'needs_enrollment' }
      }
      return { status: 'authenticated', session: response.data }
    } catch (error) {
      if (isAxiosError(error) && error.response?.data?.error === 'totp_required') {
        return { status: 'totp_required' }
      }
      if (isAxiosError(error) && error.response?.data?.error === 'invalid_totp_code') {
        return { status: 'invalid_totp_code' }
      }
      throw error
    }
  },
  logout(): Promise<void> {
    return apiClient.post('/auth/logout').then(() => undefined)
  },
  fetchSession(): Promise<SessionResponse> {
    return apiClient.get<SessionResponse>('/auth/session').then((response) => response.data)
  },
  enroll(email: string, password: string): Promise<TotpEnrollResponse> {
    return apiClient
      .post<TotpEnrollResponse>('/auth/totp/enroll', { email, password })
      .then((response) => response.data)
  },
  verify(email: string, password: string, code: string): Promise<SessionResponse> {
    return apiClient
      .post<SessionResponse>('/auth/totp/verify', { email, password, code })
      .then((response) => response.data)
  },
  register(name: string, email: string, password: string): Promise<void> {
    return apiClient.post('/auth/register', { name, email, password }).then(() => undefined)
  },
  fetchConfig(): Promise<AuthConfigResponse> {
    return apiClient.get<AuthConfigResponse>('/auth/config').then((response) => response.data)
  },
  linkGoogleAccount(linkToken: string, password: string): Promise<SessionResponse> {
    return apiClient
      .post<SessionResponse>('/auth/google/link', { linkToken, password })
      .then((response) => response.data)
  },
  fetchTrustedDevices(): Promise<TrustedDeviceResponse[]> {
    return apiClient
      .get<TrustedDeviceResponse[]>('/auth/trusted-devices')
      .then((response) => response.data)
  },
  revokeTrustedDevice(id: string): Promise<void> {
    return apiClient.delete(`/auth/trusted-devices/${id}`).then(() => undefined)
  },
}
