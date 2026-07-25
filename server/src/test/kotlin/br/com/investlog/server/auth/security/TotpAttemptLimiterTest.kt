package br.com.investlog.server.auth.security

import br.com.investlog.server.shared.exceptions.TooManyTotpAttemptsException
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertFailsWith

class TotpAttemptLimiterTest {

    @Test
    fun `allows attempts below the threshold`() {
        val limiter = TotpAttemptLimiter(maxAttemptsBeforeLockout = 3, baseLockoutDuration = Duration.ofMinutes(1))

        limiter.checkNotLocked("someone@example.com")
        limiter.recordFailure("someone@example.com")
        limiter.recordFailure("someone@example.com")

        limiter.checkNotLocked("someone@example.com")
    }

    @Test
    fun `locks the account once the threshold is reached`() {
        val limiter = TotpAttemptLimiter(maxAttemptsBeforeLockout = 3, baseLockoutDuration = Duration.ofMinutes(1))

        repeat(3) { limiter.recordFailure("someone@example.com") }

        assertFailsWith<TooManyTotpAttemptsException> { limiter.checkNotLocked("someone@example.com") }
    }

    @Test
    fun `unlocks once the lockout window elapses`() {
        val limiter = TotpAttemptLimiter(maxAttemptsBeforeLockout = 3, baseLockoutDuration = Duration.ofMillis(150))

        repeat(3) { limiter.recordFailure("someone@example.com") }
        assertFailsWith<TooManyTotpAttemptsException> { limiter.checkNotLocked("someone@example.com") }

        Thread.sleep(200)

        limiter.checkNotLocked("someone@example.com")
    }

    @Test
    fun `a success clears the failure count and any lockout`() {
        val limiter = TotpAttemptLimiter(maxAttemptsBeforeLockout = 3, baseLockoutDuration = Duration.ofMinutes(1))

        repeat(2) { limiter.recordFailure("someone@example.com") }
        limiter.recordSuccess("someone@example.com")
        limiter.recordFailure("someone@example.com")
        limiter.recordFailure("someone@example.com")

        limiter.checkNotLocked("someone@example.com")
    }

    @Test
    fun `repeated lockouts back off exponentially`() {
        val limiter = TotpAttemptLimiter(maxAttemptsBeforeLockout = 1, baseLockoutDuration = Duration.ofMillis(150))

        limiter.recordFailure("someone@example.com")
        Thread.sleep(200)
        limiter.checkNotLocked("someone@example.com")

        limiter.recordFailure("someone@example.com")
        Thread.sleep(200)
        assertFailsWith<TooManyTotpAttemptsException> { limiter.checkNotLocked("someone@example.com") }
    }

    @Test
    fun `tracks accounts independently`() {
        val limiter = TotpAttemptLimiter(maxAttemptsBeforeLockout = 2, baseLockoutDuration = Duration.ofMinutes(1))

        repeat(2) { limiter.recordFailure("locked@example.com") }

        assertFailsWith<TooManyTotpAttemptsException> { limiter.checkNotLocked("locked@example.com") }
        limiter.checkNotLocked("other@example.com")
    }
}
