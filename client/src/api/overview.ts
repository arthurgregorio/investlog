import { apiClient } from './client'
import type { AllocationGroupBy, OverviewAllocation, PortfolioSummary, SeriesPoint } from '@/types'

export const overviewApi = {
  getAllocation(groupBy: AllocationGroupBy, strategyId?: string): Promise<OverviewAllocation> {
    return apiClient
      .get<OverviewAllocation>('/overview/allocation', { params: { groupBy, strategyId } })
      .then((r) => r.data)
  },

  getSummary(): Promise<PortfolioSummary> {
    return apiClient.get<PortfolioSummary>('/overview').then((r) => r.data)
  },

  getSeries(): Promise<SeriesPoint[]> {
    return apiClient.get<SeriesPoint[]>('/overview/series').then((r) => r.data)
  },
}
