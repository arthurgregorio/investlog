package br.com.investlog.server.overview.repositories

import java.math.BigDecimal
import java.time.YearMonth

data class CapitalMonth(
    val month: YearMonth,
    val ownMoney: BigDecimal,
    val reinvestedProfit: BigDecimal,
)
