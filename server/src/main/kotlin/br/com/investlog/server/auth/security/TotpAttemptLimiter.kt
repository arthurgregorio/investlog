package br.com.investlog.server.auth.security

import br.com.investlog.server.config.InvestlogConfigurations
import br.com.investlog.server.shared.exceptions.TooManyTotpAttemptsException
import br.com.investlog.server.shared.security.AttemptLockoutTracker
import org.springframework.stereotype.Component

@Component
class TotpAttemptLimiter(investlogConfigurations: InvestlogConfigurations) {

    private val tracker = AttemptLockoutTracker(
        maxAttempts = investlogConfigurations.security.totp.lockoutMaxAttempts,
        baseDuration = investlogConfigurations.security.totp.lockoutBaseDuration,
    )

    fun checkNotLocked(email: String) {
        if (tracker.lockedUntil(email) != null) {
            throw TooManyTotpAttemptsException("Muitas tentativas de código TOTP inválidas, tente novamente mais tarde")
        }
    }

    fun recordFailure(email: String) = tracker.recordFailure(email)

    fun recordSuccess(email: String) = tracker.recordSuccess(email)
}
