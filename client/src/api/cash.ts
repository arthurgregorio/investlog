import { apiClient } from './client'
import type { CashReconciliation } from '@/types'

export interface CashTransferPayload {
  destinationWalletId: string
  transferDate: string
  amount: number
  rate?: number
}

export interface CashWithdrawalPayload {
  withdrawalDate: string
  amount: number
  bankName?: string
}

export const cashApi = {
  transfer(walletId: string, payload: CashTransferPayload): Promise<void> {
    return apiClient.post(`/wallets/${walletId}/cash-transfers`, payload).then(() => undefined)
  },

  deleteTransfer(walletId: string, transferId: string): Promise<void> {
    return apiClient
      .delete(`/wallets/${walletId}/cash-transfers/${transferId}`)
      .then(() => undefined)
  },

  withdrawToBank(walletId: string, payload: CashWithdrawalPayload): Promise<void> {
    return apiClient.post(`/wallets/${walletId}/cash-withdrawals`, payload).then(() => undefined)
  },

  deleteBankWithdrawal(walletId: string, withdrawalId: string): Promise<void> {
    return apiClient
      .delete(`/wallets/${walletId}/cash-withdrawals/${withdrawalId}`)
      .then(() => undefined)
  },

  findReconciliation(): Promise<CashReconciliation[]> {
    return apiClient.get<CashReconciliation[]>('/cash-reconciliation').then((r) => r.data)
  },

  markReconciled(walletId: string): Promise<void> {
    return apiClient.post(`/wallets/${walletId}/cash-reconciliation`).then(() => undefined)
  },
}
