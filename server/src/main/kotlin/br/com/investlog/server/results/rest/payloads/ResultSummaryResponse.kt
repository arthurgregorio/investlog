package br.com.investlog.server.results.rest.payloads

import java.math.BigDecimal

data class ResultSummaryResponse(
    val displayCurrency: String,
    val totalWithdrawn: BigDecimal,
    val totalNetReceived: BigDecimal,
    val totalProfit: BigDecimal,
    val totalFees: BigDecimal,
    val totalTaxes: BigDecimal,
    val exitCount: Int,
)
