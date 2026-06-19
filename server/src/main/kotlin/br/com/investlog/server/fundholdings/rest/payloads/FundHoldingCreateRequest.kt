package br.com.investlog.server.fundholdings.rest.payloads

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
import java.math.BigDecimal
import java.util.UUID

data class FundHoldingCreateRequest(
    @field:NotNull
    val fundTypeId: UUID,
    @field:NotBlank
    val name: String,
    @field:Valid @field:NotNull
    val contribution: ContributionCreateRequest,
    @field:PositiveOrZero
    val currentValue: BigDecimal? = null
)
