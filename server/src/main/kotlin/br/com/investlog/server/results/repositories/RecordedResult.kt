package br.com.investlog.server.results.repositories

import java.math.BigDecimal

data class RecordedResult(
    val id: Long,
    val netAmount: BigDecimal,
)
