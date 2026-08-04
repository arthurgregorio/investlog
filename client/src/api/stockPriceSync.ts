import { apiClient } from './client'

export const stockPriceSyncApi = {
  forceSync(): Promise<void> {
    return apiClient.post('/stock-price-sync').then(() => undefined)
  },
}
