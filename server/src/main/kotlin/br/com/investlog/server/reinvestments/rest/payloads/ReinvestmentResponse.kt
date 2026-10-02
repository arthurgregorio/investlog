package br.com.investlog.server.reinvestments.rest.payloads

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class ReinvestmentResponse(
    val id: UUID,
    val reinvestmentDate: LocalDate,
    val currency: String,
    val source: ReinvestmentSideResponse,
    val destination: ReinvestmentSideResponse,
    val quantity: BigDecimal?,
    val grossAmount: BigDecimal,
    val fees: BigDecimal,
    val taxes: BigDecimal,
    val amount: BigDecimal,
    val profit: BigDecimal,
)
