import { apiClient } from './client'
import type { MailSettingsRequest, MailSettingsResponse } from '@/types'

export const mailSettingsApi = {
  get(): Promise<MailSettingsResponse> {
    return apiClient.get<MailSettingsResponse>('/mail-settings').then((response) => response.data)
  },
  save(request: MailSettingsRequest): Promise<MailSettingsResponse> {
    return apiClient
      .put<MailSettingsResponse>('/mail-settings', request)
      .then((response) => response.data)
  },
  setEnabled(enabled: boolean): Promise<MailSettingsResponse> {
    return apiClient
      .patch<MailSettingsResponse>('/mail-settings/enabled', { enabled })
      .then((response) => response.data)
  },
  sendTest(recipient: string): Promise<void> {
    return apiClient.post('/mail-settings/test', { recipient }).then(() => undefined)
  },
}
