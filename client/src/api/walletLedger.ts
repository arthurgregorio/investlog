import { apiClient } from './client'
import type { PagedResponse, WalletLedgerEntry } from '@/types'

export const walletLedgerApi = {
  findAll(
    walletId: string,
    params: { page?: number; size?: number } = {},
  ): Promise<PagedResponse<WalletLedgerEntry>> {
    return apiClient
      .get<PagedResponse<WalletLedgerEntry>>(`/wallets/${walletId}/ledger`, { params })
      .then((r) => r.data)
  },
}
