package br.com.investlog.server.cash.repositories

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class CashSource(
    val resultId: UUID,
    val resultDate: LocalDate,
    val holdingName: String,
    val ticker: String?,
    val amount: BigDecimal,
)
