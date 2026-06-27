import { apiClient } from './client'
import type { PagedResponse, WalletResponse } from '@/types'

export const walletsApi = {
  findAll(): Promise<PagedResponse<WalletResponse>> {
    return apiClient
      .get<PagedResponse<WalletResponse>>('/wallets', { params: { size: 100 } })
      .then((r) => r.data)
  },

  create(payload: { name: string; kind: string; currency: string }): Promise<WalletResponse> {
    return apiClient.post<WalletResponse>('/wallets', payload).then((r) => r.data)
  },

  update(walletId: string, payload: { name: string }): Promise<WalletResponse> {
    return apiClient.patch<WalletResponse>(`/wallets/${walletId}`, payload).then((r) => r.data)
  },

  remove(walletId: string): Promise<void> {
    return apiClient.delete(`/wallets/${walletId}`).then(() => undefined)
  },
}
