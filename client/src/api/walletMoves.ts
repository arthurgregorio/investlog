import { apiClient } from './client'
import type { PagedResponse, WalletMoveRow } from '@/types'

export interface WalletMoveItemPayload {
  holdingId: string
  quantity?: number
}

export interface WalletMovePayload {
  destinationWalletId: string
  items: WalletMoveItemPayload[]
}

export const walletMovesApi = {
  findAll(
    walletId: string,
    params: { page?: number; size?: number } = {},
  ): Promise<PagedResponse<WalletMoveRow>> {
    return apiClient
      .get<PagedResponse<WalletMoveRow>>(`/wallets/${walletId}/moves`, { params })
      .then((r) => r.data)
  },

  move(originWalletId: string, payload: WalletMovePayload): Promise<void> {
    return apiClient.post(`/wallets/${originWalletId}/moves`, payload).then(() => undefined)
  },
}
