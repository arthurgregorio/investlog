package br.com.investlog.server.auth.security

import br.com.investlog.server.config.InvestlogConfigurations
import br.com.investlog.server.shared.exceptions.TooManyLoginAttemptsException
import br.com.investlog.server.shared.security.AttemptLockoutTracker
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class LoginAttemptLimiter(investlogConfigurations: InvestlogConfigurations, clock: Clock) {

    private val tracker = AttemptLockoutTracker(
        maxAttempts = investlogConfigurations.security.login.lockoutMaxAttempts,
        baseDuration = investlogConfigurations.security.login.lockoutBaseDuration,
        clock = clock,
    )

    fun checkNotLocked(attemptKey: String) {
        if (tracker.lockedUntil(attemptKey) != null) {
            throw TooManyLoginAttemptsException("Muitas tentativas de login inválidas, tente novamente mais tarde")
        }
    }

    fun recordFailure(attemptKey: String) = tracker.recordFailure(attemptKey)

    fun recordSuccess(attemptKey: String) = tracker.recordSuccess(attemptKey)

    fun clearAllFor(email: String) = tracker.clearKeysStartingWith(AttemptKeys.prefixOf(email))
}
