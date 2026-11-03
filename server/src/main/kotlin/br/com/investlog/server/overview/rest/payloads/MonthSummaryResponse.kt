package br.com.investlog.server.overview.rest.payloads

import java.math.BigDecimal

data class MonthSummaryResponse(
    val month: String,
    val contributions: BigDecimal,
    val realizedProfit: BigDecimal,
    val currentResultChange: BigDecimal?,
    val bestMover: MoverResponse?,
    val worstMover: MoverResponse?,
)
