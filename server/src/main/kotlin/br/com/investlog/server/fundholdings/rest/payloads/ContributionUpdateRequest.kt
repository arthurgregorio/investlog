package br.com.investlog.server.fundholdings.rest.payloads

import jakarta.validation.constraints.NotNull
import java.time.LocalDate

data class ContributionUpdateRequest(
    @field:NotNull
    val contributionDate: LocalDate,
)
