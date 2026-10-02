package br.com.investlog.server.results.repositories

import br.com.investlog.server.jooq.finances.enums.ResultType
import java.math.BigDecimal
import java.time.LocalDate

data class ResultRecord(
    val id: Long,
    val resultType: ResultType,
    val resultDate: LocalDate,
    val grossAmount: BigDecimal,
)
