package br.com.investlog.server.fundholdings.rest.payloads

import br.com.investlog.server.shared.validation.BoundedAmount
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
import java.math.BigDecimal
import java.util.UUID

data class FundHoldingCreateRequest(
    @field:NotNull
    val typeId: UUID,
    val segmentId: UUID? = null,
    val strategyClassId: UUID? = null,
    @field:NotBlank
    val name: String,
    @field:Valid @field:NotNull
    val contribution: ContributionCreateRequest,
    @field:PositiveOrZero
    @field:BoundedAmount
    val currentValue: BigDecimal? = null,
    @field:PositiveOrZero
    @field:BoundedAmount
    val administrationFeeRate: BigDecimal? = null,
    @field:PositiveOrZero
    @field:BoundedAmount
    val performanceFeeRate: BigDecimal? = null
)
