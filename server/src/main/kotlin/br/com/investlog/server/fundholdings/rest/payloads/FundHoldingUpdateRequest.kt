package br.com.investlog.server.fundholdings.rest.payloads

import jakarta.validation.constraints.PositiveOrZero
import java.math.BigDecimal
import java.util.UUID

data class FundHoldingUpdateRequest(
    val fundTypeId: UUID? = null,
    val name: String? = null,
    @field:PositiveOrZero val currentValue: BigDecimal? = null,
)
