import { router } from '@/router'
import { isPublicRoute } from '@/router/publicRoutes'

const STORAGE_KEY = 'investlog.postLoginRedirect'

function isSafePostLoginRedirect(path: unknown): path is string {
  if (typeof path !== 'string') return false
  if (!path.startsWith('/') || path.startsWith('//') || path.startsWith('/\\')) return false
  const route = router.resolve(path)
  return route.matched.length > 0 && !isPublicRoute(route.name ?? undefined)
}

export function rememberPostLoginRedirect(fullPath: string) {
  if (!isSafePostLoginRedirect(fullPath)) return
  try {
    sessionStorage.setItem(STORAGE_KEY, fullPath)
  } catch {
    return
  }
}

export function consumePostLoginRedirect(): string | null {
  let path: string | null
  try {
    path = sessionStorage.getItem(STORAGE_KEY)
    sessionStorage.removeItem(STORAGE_KEY)
  } catch {
    return null
  }
  return isSafePostLoginRedirect(path) ? path : null
}

export function forgetPostLoginRedirect() {
  try {
    sessionStorage.removeItem(STORAGE_KEY)
  } catch {
    return
  }
}
