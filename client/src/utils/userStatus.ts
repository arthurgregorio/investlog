import type { UserStatus } from '@/types'

export const statusTagType: Record<UserStatus, string> = {
  APPROVED: 'is-success',
  BLOCKED: 'is-danger',
  PENDING: 'is-warning',
}
