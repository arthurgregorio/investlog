import { apiClient } from './client'
import type { AssetType, HoldingType, PagedResponse, WalletKind } from '@/types'

export const assetTypesApi = {
  findAllHoldingTypes(kind?: WalletKind): Promise<HoldingType[]> {
    return apiClient
      .get<PagedResponse<HoldingType>>('/holding-types', { params: { kind, size: 200 } })
      .then((r) => r.data.content)
  },

  createHoldingType(name: string, kinds: WalletKind[]): Promise<HoldingType> {
    return apiClient.post<HoldingType>('/holding-types', { name, kinds }).then((r) => r.data)
  },

  updateHoldingType(id: string, name: string, kinds: WalletKind[]): Promise<HoldingType> {
    return apiClient.put<HoldingType>(`/holding-types/${id}`, { name, kinds }).then((r) => r.data)
  },

  removeHoldingType(id: string): Promise<void> {
    return apiClient.delete(`/holding-types/${id}`).then(() => undefined)
  },

  findAllSegments(): Promise<AssetType[]> {
    return apiClient
      .get<PagedResponse<AssetType>>('/segments', { params: { size: 200 } })
      .then((r) => r.data.content)
  },

  createSegment(name: string): Promise<AssetType> {
    return apiClient.post<AssetType>('/segments', { name }).then((r) => r.data)
  },

  updateSegment(id: string, name: string): Promise<AssetType> {
    return apiClient.put<AssetType>(`/segments/${id}`, { name }).then((r) => r.data)
  },

  removeSegment(id: string): Promise<void> {
    return apiClient.delete(`/segments/${id}`).then(() => undefined)
  },
}
