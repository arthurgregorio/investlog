package br.com.investlog.server.fundholdings.rest.payloads

import br.com.investlog.server.shared.validation.BoundedAmount
import jakarta.validation.constraints.PositiveOrZero
import java.math.BigDecimal
import java.util.UUID

data class FundHoldingUpdateRequest(
    val typeId: UUID?,
    val name: String?,
    @field:PositiveOrZero
    @field:BoundedAmount
    val currentValue: BigDecimal?,
    @field:PositiveOrZero
    @field:BoundedAmount
    val administrationFeeRate: BigDecimal?,
    @field:PositiveOrZero
    @field:BoundedAmount
    val performanceFeeRate: BigDecimal?,
    val frozen: Boolean? = null,
)
