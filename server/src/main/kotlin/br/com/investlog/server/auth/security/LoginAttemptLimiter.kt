package br.com.investlog.server.auth.security

import br.com.investlog.server.config.InvestlogConfigurations
import br.com.investlog.server.shared.exceptions.TooManyLoginAttemptsException
import br.com.investlog.server.shared.security.AttemptLockoutTracker
import org.springframework.stereotype.Component

@Component
class LoginAttemptLimiter(investlogConfigurations: InvestlogConfigurations) {

    private val tracker = AttemptLockoutTracker(
        maxAttempts = investlogConfigurations.security.login.lockoutMaxAttempts,
        baseDuration = investlogConfigurations.security.login.lockoutBaseDuration,
    )

    fun checkNotLocked(email: String) {
        if (tracker.lockedUntil(email) != null) {
            throw TooManyLoginAttemptsException("Muitas tentativas de login inválidas, tente novamente mais tarde")
        }
    }

    fun recordFailure(email: String) = tracker.recordFailure(email)

    fun recordSuccess(email: String) = tracker.recordSuccess(email)
}
