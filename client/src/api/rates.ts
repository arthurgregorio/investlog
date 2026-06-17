import { apiClient } from './client'
import type { CurrencyRate, PagedResponse } from '@/types'

export const ratesApi = {
  findAll(): Promise<CurrencyRate[]> {
    return apiClient
      .get<PagedResponse<CurrencyRate>>('/currency-rates', { params: { size: 100 } })
      .then((r) => r.data.content)
  },

  upsert(currencyCode: string, rate: number, isBase: boolean): Promise<CurrencyRate> {
    return apiClient
      .put<CurrencyRate>(`/currency-rates/${currencyCode}`, { rate, isBase })
      .then((r) => r.data)
  },
}
