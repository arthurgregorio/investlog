package br.com.investlog.server.cash.repositories

import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class CashReplayEvent(
    val sequence: Int,
    val eventType: String,
    val eventId: UUID,
    val eventDate: LocalDate,
    val createdAt: OffsetDateTime,
    val amount: BigDecimal,
    val cashUsed: BigDecimal?,
    val newMoney: BigDecimal?,
    val balance: BigDecimal,
) {
    val isFixedDebit: Boolean
        get() = eventType == TRANSFER_OUT || eventType == CASH_WITHDRAWAL

    companion object {
        const val WITHDRAWAL = "WITHDRAWAL"
        const val TRANSFER_OUT = "TRANSFER_OUT"
        const val CASH_WITHDRAWAL = "CASH_WITHDRAWAL"
    }
}
