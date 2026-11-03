import { apiClient } from './client'
import type {
  ContributionDetail,
  CryptoHoldingDetail,
  FundHoldingDetail,
  HoldingDetail,
  HoldingRow,
  LotDetail,
  PagedResponse,
  StockHoldingDetail,
  WalletKind,
} from '@/types'

const HOLDING_PATH: Record<WalletKind, string> = {
  STOCKS: 'stock-holdings',
  CRYPTO: 'crypto-holdings',
  FUNDS: 'fund-holdings',
}

export interface LotUpdatePayload {
  lotDate: string
  quantity: number
  price: number
}

export interface ContributionUpdatePayload {
  contributionDate: string
  amount: number
}

export interface ReportPdf {
  blob: Blob
  fileName: string
}

export interface HoldingsFilterParams {
  kind?: string
  typeLabel?: string | string[]
  walletId?: string | string[]
  search?: string
  hideFrozen?: boolean
}

// Spring binds a list only from repeated keys (walletId=a&walletId=b), not from axios's default
// walletId[]=a form.
const REPEATED_KEYS = { indexes: null } as const

const DEFAULT_REPORT_FILE_NAME = 'relatorio-investimentos.pdf'

function reportFileName(contentDisposition: unknown): string {
  if (typeof contentDisposition !== 'string') return DEFAULT_REPORT_FILE_NAME
  return /filename="?([^";]+)"?/i.exec(contentDisposition)?.[1] ?? DEFAULT_REPORT_FILE_NAME
}

export const holdingsApi = {
  findAll(
    params: HoldingsFilterParams & { sort?: string; page?: number; size?: number },
  ): Promise<PagedResponse<HoldingRow>> {
    return apiClient
      .get<PagedResponse<HoldingRow>>('/holdings', { params, paramsSerializer: REPEATED_KEYS })
      .then((r) => r.data)
  },

  findAllForReport(params: HoldingsFilterParams): Promise<HoldingRow[]> {
    return apiClient
      .get<HoldingRow[]>('/holdings/report', { params, paramsSerializer: REPEATED_KEYS })
      .then((r) => r.data)
  },

  downloadReportPdf(params: HoldingsFilterParams, signal?: AbortSignal): Promise<ReportPdf | null> {
    return apiClient
      .get<Blob>('/holdings/report/pdf', {
        params,
        paramsSerializer: REPEATED_KEYS,
        responseType: 'blob',
        signal,
      })
      .then((response) =>
        response.status === 204
          ? null
          : {
              blob: response.data,
              fileName: reportFileName(response.headers['content-disposition']),
            },
      )
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
      typeId: string
      segmentId?: string
      strategyClassId?: string
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

  updateHoldingSegment(
    kind: WalletKind,
    walletId: string,
    holdingId: string,
    segmentId: string | null,
  ): Promise<HoldingDetail> {
    return apiClient
      .put<HoldingDetail>(`/wallets/${walletId}/${HOLDING_PATH[kind]}/${holdingId}/segment`, {
        segmentId,
      })
      .then((r) => r.data)
  },

  updateHoldingClass(
    kind: WalletKind,
    walletId: string,
    holdingId: string,
    strategyClassId: string,
  ): Promise<HoldingDetail> {
    return apiClient
      .put<HoldingDetail>(`/wallets/${walletId}/${HOLDING_PATH[kind]}/${holdingId}/class`, {
        strategyClassId,
      })
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
    payload: { typeId?: string; currentPrice?: number; frozen?: boolean },
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
      typeId?: string
      segmentId?: string
      strategyClassId?: string
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
    payload: { typeId?: string; currentPrice?: number; frozen?: boolean },
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
      typeId: string
      segmentId?: string
      strategyClassId?: string
      name: string
      currentValue?: number
      administrationFeeRate?: number
      performanceFeeRate?: number
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
    payload: {
      typeId?: string
      currentValue?: number
      administrationFeeRate?: number
      performanceFeeRate?: number
      frozen?: boolean
    },
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

  updateStockLot(
    walletId: string,
    holdingId: string,
    lotId: string,
    payload: LotUpdatePayload,
  ): Promise<LotDetail> {
    return apiClient
      .patch<LotDetail>(`/wallets/${walletId}/stock-holdings/${holdingId}/lots/${lotId}`, payload)
      .then((r) => r.data)
  },

  updateCryptoLot(
    walletId: string,
    holdingId: string,
    lotId: string,
    payload: LotUpdatePayload,
  ): Promise<LotDetail> {
    return apiClient
      .patch<LotDetail>(`/wallets/${walletId}/crypto-holdings/${holdingId}/lots/${lotId}`, payload)
      .then((r) => r.data)
  },

  updateFundContribution(
    walletId: string,
    holdingId: string,
    contributionId: string,
    payload: ContributionUpdatePayload,
  ): Promise<ContributionDetail> {
    return apiClient
      .patch<ContributionDetail>(
        `/wallets/${walletId}/fund-holdings/${holdingId}/contributions/${contributionId}`,
        payload,
      )
      .then((r) => r.data)
  },
}
