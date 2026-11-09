package br.com.investlog.server.notifications.repositories

import java.math.BigDecimal
import java.time.LocalDate

data class RealizedProfit(
    val walletId: Long,
    val resultDate: LocalDate,
    val profit: BigDecimal,
)
