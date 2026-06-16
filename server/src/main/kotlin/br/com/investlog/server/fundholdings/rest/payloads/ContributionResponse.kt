package br.com.investlog.server.fundholdings.rest.payloads

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class ContributionResponse(
    val id: UUID,
    val contributionDate: LocalDate,
    val amount: BigDecimal,
)
