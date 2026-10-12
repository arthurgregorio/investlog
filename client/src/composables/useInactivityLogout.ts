import { onScopeDispose, watch } from 'vue'
import { lastServerActivityAt } from '@/api/serverActivity'
import { useAuthStore } from '@/stores/auth'

export const SESSION_IDLE_TIMEOUT_MINUTES = 30
const SESSION_IDLE_TIMEOUT_MILLISECONDS = SESSION_IDLE_TIMEOUT_MINUTES * 60 * 1000
const IDLE_CHECK_INTERVAL_MILLISECONDS = 30 * 1000

export function useInactivityLogout() {
  const auth = useAuthStore()
  let checkInterval: ReturnType<typeof setInterval> | undefined

  function checkIdleTime() {
    if (Date.now() - lastServerActivityAt() >= SESSION_IDLE_TIMEOUT_MILLISECONDS) {
      void auth.expireSession('inactivity')
    }
  }

  function checkWhenVisible() {
    if (document.visibilityState === 'visible') {
      checkIdleTime()
    }
  }

  function start() {
    if (checkInterval !== undefined) return
    checkInterval = setInterval(checkIdleTime, IDLE_CHECK_INTERVAL_MILLISECONDS)
    document.addEventListener('visibilitychange', checkWhenVisible)
  }

  function stop() {
    if (checkInterval === undefined) return
    clearInterval(checkInterval)
    checkInterval = undefined
    document.removeEventListener('visibilitychange', checkWhenVisible)
  }

  watch(
    () => auth.session !== null,
    (hasSession) => (hasSession ? start() : stop()),
    { immediate: true },
  )

  onScopeDispose(stop)
}
