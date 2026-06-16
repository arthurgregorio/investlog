package br.com.investlog.server.stockholdings.rest.payloads

import jakarta.validation.constraints.PositiveOrZero
import java.math.BigDecimal
import java.util.UUID

data class StockHoldingUpdateRequest(
    val stockTypeId: UUID? = null,
    val ticker: String? = null,
    val name: String? = null,
    @field:PositiveOrZero val currentPrice: BigDecimal? = null,
)
