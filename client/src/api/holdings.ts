import { apiClient } from './client'
import type {
  ContributionDetail,
  CryptoHoldingDetail,
  FundHoldingDetail,
  HoldingRow,
  LotDetail,
  PagedResponse,
  StockHoldingDetail,
} from '@/types'

export const holdingsApi = {
  findAll(params: {
    kind?: string
    typeLabel?: string
    walletId?: string
    search?: string
    sort?: string
    page?: number
    size?: number
  }): Promise<PagedResponse<HoldingRow>> {
    return apiClient.get<PagedResponse<HoldingRow>>('/holdings', { params }).then((r) => r.data)
  },

  // --- Stock holdings ---

  getStockHolding(walletId: string, holdingId: string): Promise<StockHoldingDetail> {
    return apiClient
      .get<StockHoldingDetail>(`/wallets/${walletId}/stock-holdings/${holdingId}`)
      .then((r) => r.data)
  },

  createStockHolding(
    walletId: string,
    payload: {
      stockTypeId: string
      ticker: string
      name?: string
      currentPrice?: number
      lot: { lotDate: string; quantity: number; price: number }
    },
  ): Promise<StockHoldingDetail> {
    return apiClient
      .post<StockHoldingDetail>(`/wallets/${walletId}/stock-holdings`, payload)
      .then((r) => r.data)
  },

  deleteStockHolding(walletId: string, holdingId: string): Promise<void> {
    return apiClient
      .delete(`/wallets/${walletId}/stock-holdings/${holdingId}`)
      .then(() => undefined)
  },

  updateStockHolding(
    walletId: string,
    holdingId: string,
    payload: { currentPrice?: number },
  ): Promise<StockHoldingDetail> {
    return apiClient
      .patch<StockHoldingDetail>(`/wallets/${walletId}/stock-holdings/${holdingId}`, payload)
      .then((r) => r.data)
  },

  addStockLot(
    walletId: string,
    holdingId: string,
    payload: { lotDate: string; quantity: number; price: number },
  ): Promise<LotDetail> {
    return apiClient
      .post<LotDetail>(`/wallets/${walletId}/stock-holdings/${holdingId}/lots`, payload)
      .then((r) => r.data)
  },

  // --- Crypto holdings ---

  getCryptoHolding(walletId: string, holdingId: string): Promise<CryptoHoldingDetail> {
    return apiClient
      .get<CryptoHoldingDetail>(`/wallets/${walletId}/crypto-holdings/${holdingId}`)
      .then((r) => r.data)
  },

  createCryptoHolding(
    walletId: string,
    payload: {
      ticker: string
      name?: string
      currentPrice?: number
      lot: { lotDate: string; quantity: number; price: number }
    },
  ): Promise<CryptoHoldingDetail> {
    return apiClient
      .post<CryptoHoldingDetail>(`/wallets/${walletId}/crypto-holdings`, payload)
      .then((r) => r.data)
  },

  deleteCryptoHolding(walletId: string, holdingId: string): Promise<void> {
    return apiClient
      .delete(`/wallets/${walletId}/crypto-holdings/${holdingId}`)
      .then(() => undefined)
  },

  updateCryptoHolding(
    walletId: string,
    holdingId: string,
    payload: { currentPrice?: number },
  ): Promise<CryptoHoldingDetail> {
    return apiClient
      .patch<CryptoHoldingDetail>(`/wallets/${walletId}/crypto-holdings/${holdingId}`, payload)
      .then((r) => r.data)
  },

  addCryptoLot(
    walletId: string,
    holdingId: string,
    payload: { lotDate: string; quantity: number; price: number },
  ): Promise<LotDetail> {
    return apiClient
      .post<LotDetail>(`/wallets/${walletId}/crypto-holdings/${holdingId}/lots`, payload)
      .then((r) => r.data)
  },

  // --- Fund holdings ---

  getFundHolding(walletId: string, holdingId: string): Promise<FundHoldingDetail> {
    return apiClient
      .get<FundHoldingDetail>(`/wallets/${walletId}/fund-holdings/${holdingId}`)
      .then((r) => r.data)
  },

  createFundHolding(
    walletId: string,
    payload: {
      fundTypeId: string
      name: string
      currentValue?: number
      contribution: { contributionDate: string; amount: number }
    },
  ): Promise<FundHoldingDetail> {
    return apiClient
      .post<FundHoldingDetail>(`/wallets/${walletId}/fund-holdings`, payload)
      .then((r) => r.data)
  },

  deleteFundHolding(walletId: string, holdingId: string): Promise<void> {
    return apiClient.delete(`/wallets/${walletId}/fund-holdings/${holdingId}`).then(() => undefined)
  },

  updateFundHolding(
    walletId: string,
    holdingId: string,
    payload: { currentValue?: number },
  ): Promise<FundHoldingDetail> {
    return apiClient
      .patch<FundHoldingDetail>(`/wallets/${walletId}/fund-holdings/${holdingId}`, payload)
      .then((r) => r.data)
  },

  addContribution(
    walletId: string,
    holdingId: string,
    payload: { contributionDate: string; amount: number },
  ): Promise<ContributionDetail> {
    return apiClient
      .post<ContributionDetail>(
        `/wallets/${walletId}/fund-holdings/${holdingId}/contributions`,
        payload,
      )
      .then((r) => r.data)
  },

  deleteStockLot(walletId: string, holdingId: string, lotId: string): Promise<void> {
    return apiClient
      .delete(`/wallets/${walletId}/stock-holdings/${holdingId}/lots/${lotId}`)
      .then(() => undefined)
  },

  deleteCryptoLot(walletId: string, holdingId: string, lotId: string): Promise<void> {
    return apiClient
      .delete(`/wallets/${walletId}/crypto-holdings/${holdingId}/lots/${lotId}`)
      .then(() => undefined)
  },

  deleteFundContribution(
    walletId: string,
    holdingId: string,
    contributionId: string,
  ): Promise<void> {
    return apiClient
      .delete(`/wallets/${walletId}/fund-holdings/${holdingId}/contributions/${contributionId}`)
      .then(() => undefined)
  },

  updateStockLotDate(
    walletId: string,
    holdingId: string,
    lotId: string,
    payload: { lotDate: string },
  ): Promise<LotDetail> {
    return apiClient
      .patch<LotDetail>(`/wallets/${walletId}/stock-holdings/${holdingId}/lots/${lotId}`, payload)
      .then((r) => r.data)
  },

  updateCryptoLotDate(
    walletId: string,
    holdingId: string,
    lotId: string,
    payload: { lotDate: string },
  ): Promise<LotDetail> {
    return apiClient
      .patch<LotDetail>(`/wallets/${walletId}/crypto-holdings/${holdingId}/lots/${lotId}`, payload)
      .then((r) => r.data)
  },

  updateFundContributionDate(
    walletId: string,
    holdingId: string,
    contributionId: string,
    payload: { contributionDate: string },
  ): Promise<ContributionDetail> {
    return apiClient
      .patch<ContributionDetail>(
        `/wallets/${walletId}/fund-holdings/${holdingId}/contributions/${contributionId}`,
        payload,
      )
      .then((r) => r.data)
  },
}
