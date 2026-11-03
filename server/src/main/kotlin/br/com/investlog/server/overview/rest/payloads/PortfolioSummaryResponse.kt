package br.com.investlog.server.overview.rest.payloads

import java.math.BigDecimal

data class PortfolioSummaryResponse(
    val displayCurrency: String,
    val totalCostBasis: BigDecimal,
    val totalCurrentValue: BigDecimal,
    val totalCash: BigDecimal,
    val totalGain: BigDecimal,
    val totalGainPct: BigDecimal?,
    val positionsValue: BigDecimal,
    val ownMoney: BigDecimal,
    val realizedProfit: BigDecimal,
    val lifetimeResult: BigDecimal,
    val lifetimeResultPct: BigDecimal?,
    val unpricedHoldingCount: Int,
    val cashWalletCount: Int,
    val kindSummaries: List<KindSummaryResponse>,
)
