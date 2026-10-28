import { apiClient } from './client'
import type { ClassHolding, Strategy, WalletKind } from '@/types'

export interface StrategyClassPayload {
  id?: string
  name: string
  targetPercentage: number
}

export interface HoldingTargetPayload {
  kind: WalletKind
  holdingId: string
  targetPercentage: number | null
}

export interface HoldingReferencePayload {
  kind: WalletKind
  id: string
}

export const strategiesApi = {
  findAll(): Promise<Strategy[]> {
    return apiClient.get<Strategy[]>('/strategies').then((r) => r.data)
  },

  create(name: string): Promise<Strategy> {
    return apiClient.post<Strategy>('/strategies', { name }).then((r) => r.data)
  },

  rename(id: string, name: string): Promise<Strategy> {
    return apiClient.patch<Strategy>(`/strategies/${id}`, { name }).then((r) => r.data)
  },

  remove(id: string): Promise<void> {
    return apiClient.delete(`/strategies/${id}`).then(() => undefined)
  },

  replaceClasses(id: string, classes: StrategyClassPayload[]): Promise<Strategy> {
    return apiClient.put<Strategy>(`/strategies/${id}/classes`, { classes }).then((r) => r.data)
  },

  findClassHoldings(classId: string): Promise<ClassHolding[]> {
    return apiClient
      .get<ClassHolding[]>(`/strategy-classes/${classId}/holdings`)
      .then((r) => r.data)
  },

  assignHoldings(classId: string, holdings: HoldingReferencePayload[]): Promise<void> {
    return apiClient
      .post(`/strategy-classes/${classId}/holdings`, { holdings })
      .then(() => undefined)
  },

  updateTargets(classId: string, targets: HoldingTargetPayload[]): Promise<ClassHolding[]> {
    return apiClient
      .put<ClassHolding[]>(`/strategy-classes/${classId}/targets`, { targets })
      .then((r) => r.data)
  },
}
