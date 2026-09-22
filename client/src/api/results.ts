import { apiClient } from './client'
import type { PagedResponse, ResultRow } from '@/types'

export interface HoldingWithdrawalPayload {
  resultDate: string
  quantity: number
  unitPrice: number
  fees: number
  taxes: number
}

export interface FundWithdrawalPayload {
  resultDate: string
  amount: number
  fees: number
  taxes: number
}

export const resultsApi = {
  findAll(params: { page?: number; size?: number } = {}): Promise<PagedResponse<ResultRow>> {
    return apiClient.get<PagedResponse<ResultRow>>('/results', { params }).then((r) => r.data)
  },

  withdrawFromStockHolding(
    walletId: string,
    holdingId: string,
    payload: HoldingWithdrawalPayload,
  ): Promise<void> {
    return apiClient
      .post(`/wallets/${walletId}/stock-holdings/${holdingId}/withdrawals`, payload)
      .then(() => undefined)
  },

  withdrawFromCryptoHolding(
    walletId: string,
    holdingId: string,
    payload: HoldingWithdrawalPayload,
  ): Promise<void> {
    return apiClient
      .post(`/wallets/${walletId}/crypto-holdings/${holdingId}/withdrawals`, payload)
      .then(() => undefined)
  },

  withdrawFromFundHolding(
    walletId: string,
    holdingId: string,
    payload: FundWithdrawalPayload,
  ): Promise<void> {
    return apiClient
      .post(`/wallets/${walletId}/fund-holdings/${holdingId}/withdrawals`, payload)
      .then(() => undefined)
  },
}
