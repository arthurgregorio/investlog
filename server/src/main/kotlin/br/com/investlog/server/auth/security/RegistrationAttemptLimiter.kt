package br.com.investlog.server.auth.security

import br.com.investlog.server.config.InvestlogConfigurations
import br.com.investlog.server.shared.exceptions.TooManyRegistrationAttemptsException
import br.com.investlog.server.shared.security.AttemptLockoutTracker
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class RegistrationAttemptLimiter(investlogConfigurations: InvestlogConfigurations, clock: Clock) {

    private val tracker = AttemptLockoutTracker(
        maxAttempts = investlogConfigurations.security.registrationLimit.maxAttempts,
        baseDuration = investlogConfigurations.security.registrationLimit.baseDuration,
        clock = clock,
        failureWindow = investlogConfigurations.security.registrationLimit.failureWindow,
    )

    fun checkAndRecord(clientIp: String) {
        if (tracker.lockedUntil(clientIp) != null) {
            throw TooManyRegistrationAttemptsException("Muitas tentativas de cadastro, tente novamente mais tarde")
        }
        tracker.recordFailure(clientIp)
    }
}
