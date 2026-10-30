package br.com.investlog.server.cash.rest.payloads

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class CashSourceResponse(
    val resultId: UUID,
    val date: LocalDate,
    val holdingName: String,
    val ticker: String?,
    val amount: BigDecimal,
)
