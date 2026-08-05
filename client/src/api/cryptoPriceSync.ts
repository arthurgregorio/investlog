import { apiClient } from './client'

export const cryptoPriceSyncApi = {
  forceSync(): Promise<void> {
    return apiClient.post('/crypto-price-sync').then(() => undefined)
  },
}
