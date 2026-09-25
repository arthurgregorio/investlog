package br.com.investlog.server.shared.security

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class MutableClock(private var currentInstant: Instant) : Clock() {

    fun advance(duration: Duration) {
        currentInstant = currentInstant.plus(duration)
    }

    override fun instant(): Instant = currentInstant

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this
}
