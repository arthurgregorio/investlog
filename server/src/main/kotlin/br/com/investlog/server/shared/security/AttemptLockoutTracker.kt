package br.com.investlog.server.shared.security

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class AttemptLockoutTracker(
    private val maxAttempts: Int,
    private val baseDuration: Duration,
) {

    private val attemptStateByKey = ConcurrentHashMap<String, AttemptState>()

    fun lockedUntil(key: String): Instant? {
        val lockedUntil = attemptStateByKey[key]?.lockedUntil ?: return null
        return lockedUntil.takeIf { Instant.now().isBefore(it) }
    }

    fun recordFailure(key: String) {

        attemptStateByKey.compute(key) { _, existingState ->
            val failureCount = (existingState?.failureCount ?: 0) + 1

            if (failureCount < maxAttempts) {
                existingState?.copy(failureCount = failureCount)
                    ?: AttemptState(failureCount = failureCount, lockoutCount = 0, lockedUntil = null)
            } else {
                val lockoutCount = (existingState?.lockoutCount ?: 0) + 1
                val backoffMultiplier = 1L shl (lockoutCount - 1).coerceAtMost(MAX_BACKOFF_EXPONENT)
                AttemptState(
                    failureCount = 0,
                    lockoutCount = lockoutCount,
                    lockedUntil = Instant.now().plus(baseDuration.multipliedBy(backoffMultiplier)),
                )
            }
        }
    }

    fun recordSuccess(key: String) {
        attemptStateByKey.remove(key)
    }

    private data class AttemptState(val failureCount: Int, val lockoutCount: Int, val lockedUntil: Instant?)

    companion object {
        private const val MAX_BACKOFF_EXPONENT = 5
    }
}
