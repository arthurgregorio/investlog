package br.com.investlog.server.overview.repositories

import java.math.BigDecimal

data class MoverCandidate(
    val kind: String,
    val ticker: String?,
    val name: String?,
    val currentPrice: BigDecimal,
    val baselinePrice: BigDecimal?,
)
