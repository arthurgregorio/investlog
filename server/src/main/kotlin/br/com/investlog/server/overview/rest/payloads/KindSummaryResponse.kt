package br.com.investlog.server.overview.rest.payloads

import java.math.BigDecimal

data class KindSummaryResponse(
    val kind: String,
    val holdingCount: Int,
    val totalCostBasis: BigDecimal,
    val totalCurrentValue: BigDecimal,
    val totalGain: BigDecimal,
    val totalGainPct: BigDecimal?,
)
