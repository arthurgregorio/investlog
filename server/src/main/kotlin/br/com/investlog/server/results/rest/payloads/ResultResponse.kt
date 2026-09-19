package br.com.investlog.server.results.rest.payloads

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class ResultResponse(
    val id: UUID,
    val kind: String,
    val resultType: String,
    val holdingName: String,
    val ticker: String?,
    val walletId: UUID,
    val walletName: String,
    val walletCurrency: String,
    val resultDate: LocalDate,
    val quantity: BigDecimal?,
    val grossAmount: BigDecimal,
    val fees: BigDecimal,
    val taxes: BigDecimal,
    val costBasis: BigDecimal,
    val netAmount: BigDecimal,
    val profit: BigDecimal,
)
