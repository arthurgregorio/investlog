import { apiClient } from './client'
import type { ProfileResponse } from '@/types'

export const profileApi = {
  getProfile(): Promise<ProfileResponse> {
    return apiClient.get<ProfileResponse>('/profile').then((response) => response.data)
  },
}
