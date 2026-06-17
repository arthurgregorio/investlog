import { apiClient } from './client'
import type { PortfolioSummary, SeriesPoint } from '@/types'

export const overviewApi = {
  getSummary(): Promise<PortfolioSummary> {
    return apiClient.get<PortfolioSummary>('/overview').then((r) => r.data)
  },

  getSeries(): Promise<SeriesPoint[]> {
    return apiClient.get<SeriesPoint[]>('/overview/series').then((r) => r.data)
  },
}
