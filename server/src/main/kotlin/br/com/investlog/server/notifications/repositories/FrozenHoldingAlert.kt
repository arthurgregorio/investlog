package br.com.investlog.server.notifications.repositories

import java.time.OffsetDateTime

data class FrozenHoldingAlert(
    val green: Boolean,
    val greenSince: OffsetDateTime?,
    val alertedAt: OffsetDateTime?,
) {

    val isNew: Boolean
        get() = green && alertedAt == null

    fun alerted(alertedAt: OffsetDateTime): FrozenHoldingAlert = copy(alertedAt = alertedAt)

    companion object {
        fun after(previous: FrozenHoldingAlert?, green: Boolean, checkedAt: OffsetDateTime): FrozenHoldingAlert =
            when {
                !green -> FrozenHoldingAlert(green = false, greenSince = null, alertedAt = previous?.alertedAt)
                previous?.green == true -> previous
                else -> FrozenHoldingAlert(green = true, greenSince = checkedAt, alertedAt = null)
            }
    }
}
