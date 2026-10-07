package br.com.investlog.server.auth.security

import br.com.investlog.server.config.InvestlogConfigurations
import br.com.investlog.server.shared.exceptions.TooManyLoginAttemptsException
import br.com.investlog.server.shared.security.AttemptLockoutTracker
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class IpAttemptLimiter(investlogConfigurations: InvestlogConfigurations, clock: Clock) {

    private val tracker = AttemptLockoutTracker(
        maxAttempts = investlogConfigurations.security.ipLockout.maxAttempts,
        baseDuration = investlogConfigurations.security.ipLockout.baseDuration,
        clock = clock,
        failureWindow = investlogConfigurations.security.ipLockout.failureWindow,
    )

    fun checkNotLocked(clientIp: String) {
        if (tracker.lockedUntil(clientIp) != null) {
            throw TooManyLoginAttemptsException("Muitas tentativas de login inválidas, tente novamente mais tarde")
        }
    }

    fun recordFailure(clientIp: String) = tracker.recordFailure(clientIp)
}
