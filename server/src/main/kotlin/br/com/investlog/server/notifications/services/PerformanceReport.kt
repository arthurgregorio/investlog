package br.com.investlog.server.notifications.services

import br.com.investlog.server.overview.rest.payloads.MonthSummaryResponse
import java.math.BigDecimal

data class PerformanceReport(
    val period: ReportPeriod,
    val displayCurrency: String,
    val wallets: List<PerformanceReportWallet>,
    val totalValue: BigDecimal,
    val totalCurrentResult: BigDecimal,
    val totalPeriodResult: BigDecimal,
    val totalCurrentResultChange: BigDecimal,
    val totalGainPercentage: BigDecimal?,
    val month: MonthSummaryResponse? = null,
)
