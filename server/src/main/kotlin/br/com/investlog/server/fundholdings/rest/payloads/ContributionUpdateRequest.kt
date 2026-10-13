package br.com.investlog.server.fundholdings.rest.payloads

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal
import java.time.LocalDate

data class ContributionUpdateRequest(
    @field:NotNull
    val contributionDate: LocalDate,
    @field:Positive
    val amount: BigDecimal? = null,
)
