import type { RouteRecordNameGeneric } from 'vue-router'

export const PUBLIC_ROUTE_NAMES = new Set(['login', 'pending-approval'])

export function isPublicRoute(name: RouteRecordNameGeneric): boolean {
  return PUBLIC_ROUTE_NAMES.has(name as string)
}
