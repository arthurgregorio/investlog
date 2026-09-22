import { apiClient } from './client'
import type { WalletDetail } from '@/types'

export const walletDetailApi = {
  get(walletId: string): Promise<WalletDetail> {
    return apiClient.get<WalletDetail>(`/wallets/${walletId}/detail`).then((r) => r.data)
  },
}
