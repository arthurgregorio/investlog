package br.com.investlog.server.stockholdings.rest.payloads

import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.PositiveOrZero
import java.math.BigDecimal
import java.util.UUID

data class StockHoldingUpdateRequest(
    val stockTypeId: UUID? = null,
    @field:Pattern(regexp = "^[A-Za-z0-9]+$", message = "ticker deve conter apenas letras e números")
    val ticker: String? = null,
    val name: String? = null,
    @field:PositiveOrZero val currentPrice: BigDecimal? = null,
)
