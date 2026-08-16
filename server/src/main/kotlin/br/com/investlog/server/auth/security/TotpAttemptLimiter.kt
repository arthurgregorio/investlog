package br.com.investlog.server.auth.security

import br.com.investlog.server.config.InvestlogConfigurations
import br.com.investlog.server.shared.exceptions.TooManyTotpAttemptsException
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
class TotpAttemptLimiter(
    private val investlogConfigurations: InvestlogConfigurations
) {

    private val attemptStateByEmail = ConcurrentHashMap<String, AttemptState>()

    fun checkNotLocked(email: String) {
        val lockedUntil = attemptStateByEmail[email]?.lockedUntil ?: return
        if (Instant.now().isBefore(lockedUntil)) {
            throw TooManyTotpAttemptsException("Muitas tentativas de código TOTP inválidas, tente novamente mais tarde")
        }
    }

    fun recordFailure(email: String) {

        val totpConfigs = investlogConfigurations.security.totp

        attemptStateByEmail.compute(email) { _, existingState ->
            val failureCount = (existingState?.failureCount ?: 0) + 1

            if (failureCount < totpConfigs.lockoutMaxAttempts) {
                existingState?.copy(failureCount = failureCount)
                    ?: AttemptState(failureCount = failureCount, lockoutCount = 0, lockedUntil = null)
            } else {
                val lockoutCount = (existingState?.lockoutCount ?: 0) + 1
                val backoffMultiplier = 1L shl (lockoutCount - 1).coerceAtMost(MAX_BACKOFF_EXPONENT)
                AttemptState(
                    failureCount = 0,
                    lockoutCount = lockoutCount,
                    lockedUntil = Instant.now()
                        .plus(totpConfigs.lockoutBaseDuration.multipliedBy(backoffMultiplier)),
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
