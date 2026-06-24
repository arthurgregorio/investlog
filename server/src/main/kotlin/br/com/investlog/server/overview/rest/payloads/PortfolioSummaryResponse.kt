package br.com.investlog.server.overview.rest.payloads

import java.math.BigDecimal

data class PortfolioSummaryResponse(
    val displayCurrency: String,
    val totalCostBasis: BigDecimal,
    val totalCurrentValue: BigDecimal,
    val totalGain: BigDecimal,
    val totalGainPct: BigDecimal?,
    val kindSummaries: List<KindSummaryResponse>,
)
