package br.com.investlog.server.shared.security

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AttemptLockoutTrackerTest {

    private val clock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))

    private fun trackerWith(failureWindow: Duration?) = AttemptLockoutTracker(
        maxAttempts = 3,
        baseDuration = Duration.ofMinutes(1),
        clock = clock,
        failureWindow = failureWindow,
    )

    @Test
    fun `failures older than the window stop counting toward a lockout`() {
        val tracker = trackerWith(failureWindow = Duration.ofMinutes(10))

        repeat(2) { tracker.recordFailure("203.0.113.9") }
        clock.advance(Duration.ofMinutes(11))
        tracker.recordFailure("203.0.113.9")

        assertNull(tracker.lockedUntil("203.0.113.9"))
    }

    @Test
    fun `failures inside the window still add up to a lockout`() {
        val tracker = trackerWith(failureWindow = Duration.ofMinutes(10))

        tracker.recordFailure("203.0.113.9")
        clock.advance(Duration.ofMinutes(9))
        tracker.recordFailure("203.0.113.9")
        clock.advance(Duration.ofMinutes(9))
        tracker.recordFailure("203.0.113.9")

        assertNotNull(tracker.lockedUntil("203.0.113.9"))
    }

    @Test
    fun `without a window failures never expire`() {
        val tracker = trackerWith(failureWindow = null)

        repeat(2) { tracker.recordFailure("someone@example.com|203.0.113.9") }
        clock.advance(Duration.ofDays(30))
        tracker.recordFailure("someone@example.com|203.0.113.9")

        assertNotNull(tracker.lockedUntil("someone@example.com|203.0.113.9"))
    }

    @Test
    fun `clearing by prefix removes every key of that account and no other`() {
        val tracker = trackerWith(failureWindow = null)
        val keys = listOf("someone@example.com|203.0.113.9", "someone@example.com|198.51.100.4", "other@example.com|203.0.113.9")
        keys.forEach { key -> repeat(3) { tracker.recordFailure(key) } }

        tracker.clearKeysStartingWith("someone@example.com|")

        assertNull(tracker.lockedUntil(keys[0]))
        assertNull(tracker.lockedUntil(keys[1]))
        assertNotNull(tracker.lockedUntil(keys[2]))
    }

    @Test
    fun `a lockout keeps its escalating backoff when a window is configured`() {
        val tracker = trackerWith(failureWindow = Duration.ofMinutes(10))

        repeat(3) { tracker.recordFailure("203.0.113.9") }
        assertEquals(clock.instant().plus(Duration.ofMinutes(1)), tracker.lockedUntil("203.0.113.9"))

        clock.advance(Duration.ofMinutes(1))
        repeat(3) { tracker.recordFailure("203.0.113.9") }

        assertEquals(clock.instant().plus(Duration.ofMinutes(2)), tracker.lockedUntil("203.0.113.9"))
    }
}
