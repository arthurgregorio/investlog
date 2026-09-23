package br.com.investlog.server.results.rest.payloads

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class WithdrawalResponse(
    val id: UUID,
    val resultDate: LocalDate,
    val quantity: BigDecimal?,
    val grossAmount: BigDecimal,
    val fees: BigDecimal,
    val taxes: BigDecimal,
    val costBasis: BigDecimal,
    val netAmount: BigDecimal,
    val profit: BigDecimal,
)
