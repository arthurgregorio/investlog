import { apiClient } from './client'

export interface WalletMoveItemPayload {
  holdingId: string
  quantity?: number
}

export interface WalletMovePayload {
  destinationWalletId: string
  items: WalletMoveItemPayload[]
}

export const walletMovesApi = {
  move(originWalletId: string, payload: WalletMovePayload): Promise<void> {
    return apiClient.post(`/wallets/${originWalletId}/moves`, payload).then(() => undefined)
  },
}
