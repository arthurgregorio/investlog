package br.com.investlog.server.cash.rest.payloads

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class CashSuggestionResponse(
    val walletId: UUID,
    val walletName: String,
    val walletCurrency: String,
    val holdingName: String,
    val ticker: String?,
    val purchaseDate: LocalDate,
    val newMoney: BigDecimal,
    val rate: BigDecimal,
)
