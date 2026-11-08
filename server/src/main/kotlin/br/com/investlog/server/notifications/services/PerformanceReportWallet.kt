package br.com.investlog.server.notifications.services

import java.math.BigDecimal
import java.time.LocalDate

data class PerformanceReportWallet(
    val name: String,
    val currency: String,
    val currentValue: BigDecimal,
    val totalInvested: BigDecimal,
    val currentResult: BigDecimal,
    val realizedProfit: BigDecimal,
    val currentResultChange: BigDecimal,
    val periodResult: BigDecimal,
    val gainPercentage: BigDecimal,
    val newSince: LocalDate?,
)
