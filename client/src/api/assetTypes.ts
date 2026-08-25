import { apiClient } from './client'
import type { AssetType, PagedResponse } from '@/types'

export const assetTypesApi = {
  findAllStockTypes(): Promise<AssetType[]> {
    return apiClient
      .get<PagedResponse<AssetType>>('/stock-types', { params: { size: 200 } })
      .then((r) => r.data.content)
  },

  createStockType(name: string): Promise<AssetType> {
    return apiClient.post<AssetType>('/stock-types', { name }).then((r) => r.data)
  },

  updateStockType(id: string, name: string): Promise<AssetType> {
    return apiClient.put<AssetType>(`/stock-types/${id}`, { name }).then((r) => r.data)
  },

  removeStockType(id: string): Promise<void> {
    return apiClient.delete(`/stock-types/${id}`).then(() => undefined)
  },

  findAllFundTypes(): Promise<AssetType[]> {
    return apiClient
      .get<PagedResponse<AssetType>>('/fund-types', { params: { size: 200 } })
      .then((r) => r.data.content)
  },

  createFundType(name: string): Promise<AssetType> {
    return apiClient.post<AssetType>('/fund-types', { name }).then((r) => r.data)
  },

  updateFundType(id: string, name: string): Promise<AssetType> {
    return apiClient.put<AssetType>(`/fund-types/${id}`, { name }).then((r) => r.data)
  },

  removeFundType(id: string): Promise<void> {
    return apiClient.delete(`/fund-types/${id}`).then(() => undefined)
  },
}
