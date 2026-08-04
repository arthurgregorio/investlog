import { apiClient } from './client'
import type { ConfigurationResponse, PagedResponse } from '@/types'

export const configurationsApi = {
  findAll(): Promise<ConfigurationResponse[]> {
    return apiClient
      .get<PagedResponse<ConfigurationResponse>>('/configurations', { params: { size: 100 } })
      .then((r) => r.data.content)
  },

  update(key: string, value: string): Promise<ConfigurationResponse> {
    return apiClient
      .patch<ConfigurationResponse>(`/configurations/${key}`, { value })
      .then((r) => r.data)
  },
}
