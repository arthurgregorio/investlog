import { apiClient } from './client'
import type { PagedResponse, ReinvestmentRow, WalletKind } from '@/types'

export interface ReinvestmentPayload {
  sourceKind: WalletKind
  sourceHoldingId: string
  destinationKind: WalletKind
  destinationHoldingId: string
  reinvestmentDate: string
  quantity?: number
  unitPrice?: number
  amount?: number
  fees: number
  taxes: number
}

export const reinvestmentsApi = {
  findAll(params: { page?: number; size?: number } = {}): Promise<PagedResponse<ReinvestmentRow>> {
    return apiClient
      .get<PagedResponse<ReinvestmentRow>>('/reinvestments', { params })
      .then((r) => r.data)
  },

  reinvest(payload: ReinvestmentPayload): Promise<void> {
    return apiClient.post('/reinvestments', payload).then(() => undefined)
  },
}
