import { apiClient } from './client'
import type { ProfileResponse, ProfileUpdateRequest } from '@/types'

export const profileApi = {
  getProfile(): Promise<ProfileResponse> {
    return apiClient.get<ProfileResponse>('/profile').then((response) => response.data)
  },
  updateProfile(request: ProfileUpdateRequest): Promise<ProfileResponse> {
    return apiClient.patch<ProfileResponse>('/profile', request).then((response) => response.data)
  },
  changePassword(currentPassword: string, newPassword: string): Promise<void> {
    return apiClient
      .patch('/profile/password', { currentPassword, newPassword })
      .then(() => undefined)
  },
}
