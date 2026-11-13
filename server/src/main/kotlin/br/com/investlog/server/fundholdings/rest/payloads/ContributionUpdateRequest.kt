package br.com.investlog.server.fundholdings.rest.payloads

import br.com.investlog.server.shared.validation.BoundedAmount
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal
import java.time.LocalDate

data class ContributionUpdateRequest(
    @field:NotNull
    val contributionDate: LocalDate,
    @field:Positive
    @field:BoundedAmount
    val amount: BigDecimal? = null,
)
