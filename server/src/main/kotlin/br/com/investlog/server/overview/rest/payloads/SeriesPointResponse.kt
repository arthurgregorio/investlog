package br.com.investlog.server.overview.rest.payloads

import java.math.BigDecimal

data class SeriesPointResponse(
    val month: String,
    val ownMoney: BigDecimal,
    val reinvestedProfit: BigDecimal,
    val totalInvested: BigDecimal,
)
