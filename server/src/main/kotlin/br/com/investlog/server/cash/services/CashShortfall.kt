package br.com.investlog.server.cash.services

import java.math.BigDecimal
import java.time.LocalDate

data class CashShortfall(
    val date: LocalDate,
    val available: BigDecimal,
    val currency: String,
)
