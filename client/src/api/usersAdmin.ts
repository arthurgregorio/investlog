import { apiClient } from './client'
import type { PagedResponse, UserAdminResponse, UserRole } from '@/types'

export const usersAdminApi = {
  findAll(): Promise<UserAdminResponse[]> {
    return apiClient
      .get<PagedResponse<UserAdminResponse>>('/users', { params: { size: 200 } })
      .then((response) => response.data.content)
  },

  approve(id: string): Promise<UserAdminResponse> {
    return apiClient
      .patch<UserAdminResponse>(`/users/${id}/approve`)
      .then((response) => response.data)
  },

  block(id: string): Promise<UserAdminResponse> {
    return apiClient
      .patch<UserAdminResponse>(`/users/${id}/block`)
      .then((response) => response.data)
  },

  unblock(id: string): Promise<UserAdminResponse> {
    return apiClient
      .patch<UserAdminResponse>(`/users/${id}/unblock`)
      .then((response) => response.data)
  },

  changeRole(id: string, role: UserRole): Promise<UserAdminResponse> {
    return apiClient
      .patch<UserAdminResponse>(`/users/${id}/role`, { role })
      .then((response) => response.data)
  },

  resetTotp(id: string): Promise<UserAdminResponse> {
    return apiClient
      .patch<UserAdminResponse>(`/users/${id}/totp-reset`)
      .then((response) => response.data)
  },

  resetPassword(id: string, newPassword: string): Promise<UserAdminResponse> {
    return apiClient
      .patch<UserAdminResponse>(`/users/${id}/password`, { newPassword })
      .then((response) => response.data)
  },

  remove(id: string): Promise<void> {
    return apiClient.delete(`/users/${id}`).then(() => undefined)
  },
}
