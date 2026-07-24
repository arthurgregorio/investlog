package br.com.investlog.server.auth.security

import br.com.investlog.server.shared.exceptions.TooManyTotpAttemptsException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory, per-account lockout for repeated invalid TOTP codes (RFC 6238 §5.2). Shared between
 * [br.com.investlog.server.auth.domain.services.AuthService.login] and `.verifyTotp` so an
 * attacker can't dodge the counter by switching endpoints. Keyed by email rather than IP: this is
 * a self-hosted, single/few-user app, so the map stays bounded by account count and needs no
 * eviction sweep, and per-account keying avoids trusting a client-supplied/proxy `X-Forwarded-For`
 * header that nothing else in this app currently validates.
 */
@Component
class TotpAttemptLimiter(
    @Value("\${investlog.totp-lockout.max-attempts:5}") private val maxAttemptsBeforeLockout: Int,
    @Value("\${investlog.totp-lockout.base-duration:60s}") private val baseLockoutDuration: Duration,
) {

    private val attemptStateByEmail = ConcurrentHashMap<String, AttemptState>()

    fun checkNotLocked(email: String) {
        val lockedUntil = attemptStateByEmail[email]?.lockedUntil ?: return
        if (Instant.now().isBefore(lockedUntil)) {
            throw TooManyTotpAttemptsException("Too many invalid TOTP attempts, try again later")
        }
    }

    fun recordFailure(email: String) {
        attemptStateByEmail.compute(email) { _, existingState ->
            val failureCount = (existingState?.failureCount ?: 0) + 1

            if (failureCount < maxAttemptsBeforeLockout) {
                existingState?.copy(failureCount = failureCount)
                    ?: AttemptState(failureCount = failureCount, lockoutCount = 0, lockedUntil = null)
            } else {
                val lockoutCount = (existingState?.lockoutCount ?: 0) + 1
                val backoffMultiplier = 1L shl (lockoutCount - 1).coerceAtMost(MAX_BACKOFF_EXPONENT)
                AttemptState(
                    failureCount = 0,
                    lockoutCount = lockoutCount,
                    lockedUntil = Instant.now().plus(baseLockoutDuration.multipliedBy(backoffMultiplier)),
                )
            }
        }
    }

    fun recordSuccess(email: String) {
        attemptStateByEmail.remove(email)
    }

    private data class AttemptState(val failureCount: Int, val lockoutCount: Int, val lockedUntil: Instant?)

    companion object {
        private const val MAX_BACKOFF_EXPONENT = 5
    }
}
