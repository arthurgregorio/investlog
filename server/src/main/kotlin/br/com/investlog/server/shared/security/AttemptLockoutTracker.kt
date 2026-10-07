package br.com.investlog.server.shared.security

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class AttemptLockoutTracker(
    private val maxAttempts: Int,
    private val baseDuration: Duration,
    private val clock: Clock,
    private val failureWindow: Duration? = null,
) {

    private val attemptStateByKey = ConcurrentHashMap<String, AttemptState>()

    fun lockedUntil(key: String): Instant? {
        val lockedUntil = attemptStateByKey[key]?.lockedUntil ?: return null
        return lockedUntil.takeIf { clock.instant().isBefore(it) }
    }

    fun recordFailure(key: String) {

        val now = clock.instant()

        attemptStateByKey.compute(key) { _, existingState ->
            val failureCount = (existingState.freshFailureCount(now)) + 1

            if (failureCount < maxAttempts) {
                AttemptState(
                    failureCount = failureCount,
                    lockoutCount = existingState?.lockoutCount ?: 0,
                    lockedUntil = existingState?.lockedUntil,
                    lastFailureAt = now,
                )
            } else {
                val lockoutCount = (existingState?.lockoutCount ?: 0) + 1
                val backoffMultiplier = 1L shl (lockoutCount - 1).coerceAtMost(MAX_BACKOFF_EXPONENT)
                AttemptState(
                    failureCount = 0,
                    lockoutCount = lockoutCount,
                    lockedUntil = now.plus(baseDuration.multipliedBy(backoffMultiplier)),
                    lastFailureAt = now,
                )
            }
        }
    }

    fun recordSuccess(key: String) {
        attemptStateByKey.remove(key)
    }

    fun clearKeysStartingWith(prefix: String) {
        attemptStateByKey.keys.removeIf { it.startsWith(prefix) }
    }

    private fun AttemptState?.freshFailureCount(now: Instant): Int {
        if (this == null) return 0
        val window = failureWindow ?: return failureCount
        return if (Duration.between(lastFailureAt, now) > window) 0 else failureCount
    }

    private data class AttemptState(
        val failureCount: Int,
        val lockoutCount: Int,
        val lockedUntil: Instant?,
        val lastFailureAt: Instant,
    )

    companion object {
        private const val MAX_BACKOFF_EXPONENT = 5
    }
}
