package br.com.investlog.server.results.repositories

import java.math.BigDecimal
import java.time.LocalDate

data class ResultRecord(
    val id: Long,
    val resultDate: LocalDate,
    val grossAmount: BigDecimal,
)
