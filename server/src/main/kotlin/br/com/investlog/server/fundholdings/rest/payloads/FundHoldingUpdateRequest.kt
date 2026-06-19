package br.com.investlog.server.fundholdings.rest.payloads

import jakarta.validation.constraints.PositiveOrZero
import java.math.BigDecimal
import java.util.UUID

data class FundHoldingUpdateRequest(
    val fundTypeId: UUID?,
    val name: String?,
    @field:PositiveOrZero
    val currentValue: BigDecimal?
)
