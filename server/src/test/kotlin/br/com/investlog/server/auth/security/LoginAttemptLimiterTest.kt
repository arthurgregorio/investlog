package br.com.investlog.server.auth.security

import br.com.investlog.server.config.InvestlogConfigurations
import br.com.investlog.server.shared.exceptions.TooManyLoginAttemptsException
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertFailsWith

class LoginAttemptLimiterTest {

    private fun limiterWith(maxAttemptsBeforeLockout: Int, baseLockoutDuration: Duration): LoginAttemptLimiter =
        LoginAttemptLimiter(
            InvestlogConfigurations(
                demoMode = InvestlogConfigurations.DemoMode(enabled = false),
                security = InvestlogConfigurations.Security(
                    adminDefaultPassword = "admin",
                    totp = InvestlogConfigurations.Security.Totp(
                        enabled = true,
                        lockoutMaxAttempts = 5,
                        lockoutBaseDuration = Duration.ofMinutes(1),
                    ),
                    login = InvestlogConfigurations.Security.Login(
                        lockoutMaxAttempts = maxAttemptsBeforeLockout,
                        lockoutBaseDuration = baseLockoutDuration,
                    ),
                    trustedDevice = InvestlogConfigurations.Security.TrustedDevice(expiry = Duration.ofDays(30)),
                ),
                googleAuth = InvestlogConfigurations.GoogleAuth(
                    enabled = false,
                    clientId = "",
                    clientSecret = "",
                    clientBaseUrl = "",
                ),
                brApi = InvestlogConfigurations.BrApi(baseUrl = "", token = ""),
                coinGecko = InvestlogConfigurations.CoinGecko(baseUrl = "", apiKey = "", apiKeyHeader = ""),
                awesomeApi = InvestlogConfigurations.AwesomeApi(baseUrl = ""),
            )
        )

    @Test
    fun `allows attempts below the threshold`() {
        val limiter = limiterWith(maxAttemptsBeforeLockout = 3, baseLockoutDuration = Duration.ofMinutes(1))

        limiter.checkNotLocked("someone@example.com")
        limiter.recordFailure("someone@example.com")
        limiter.recordFailure("someone@example.com")

        limiter.checkNotLocked("someone@example.com")
    }

    @Test
    fun `locks the account once the threshold is reached`() {
        val limiter = limiterWith(maxAttemptsBeforeLockout = 3, baseLockoutDuration = Duration.ofMinutes(1))

        repeat(3) { limiter.recordFailure("someone@example.com") }

        assertFailsWith<TooManyLoginAttemptsException> { limiter.checkNotLocked("someone@example.com") }
    }

    @Test
    fun `unlocks once the lockout window elapses`() {
        val limiter = limiterWith(maxAttemptsBeforeLockout = 3, baseLockoutDuration = Duration.ofMillis(150))

        repeat(3) { limiter.recordFailure("someone@example.com") }
        assertFailsWith<TooManyLoginAttemptsException> { limiter.checkNotLocked("someone@example.com") }

        Thread.sleep(200)

        limiter.checkNotLocked("someone@example.com")
    }

    @Test
    fun `a success clears the failure count and any lockout`() {
        val limiter = limiterWith(maxAttemptsBeforeLockout = 3, baseLockoutDuration = Duration.ofMinutes(1))

        repeat(2) { limiter.recordFailure("someone@example.com") }
        limiter.recordSuccess("someone@example.com")
        limiter.recordFailure("someone@example.com")
        limiter.recordFailure("someone@example.com")

        limiter.checkNotLocked("someone@example.com")
    }

    @Test
    fun `repeated lockouts back off exponentially`() {
        val limiter = limiterWith(maxAttemptsBeforeLockout = 1, baseLockoutDuration = Duration.ofMillis(150))

        limiter.recordFailure("someone@example.com")
        Thread.sleep(200)
        limiter.checkNotLocked("someone@example.com")

        limiter.recordFailure("someone@example.com")
        Thread.sleep(200)
        assertFailsWith<TooManyLoginAttemptsException> { limiter.checkNotLocked("someone@example.com") }
    }

    @Test
    fun `tracks accounts independently`() {
        val limiter = limiterWith(maxAttemptsBeforeLockout = 2, baseLockoutDuration = Duration.ofMinutes(1))

        repeat(2) { limiter.recordFailure("locked@example.com") }

        assertFailsWith<TooManyLoginAttemptsException> { limiter.checkNotLocked("locked@example.com") }
        limiter.checkNotLocked("other@example.com")
    }
}
