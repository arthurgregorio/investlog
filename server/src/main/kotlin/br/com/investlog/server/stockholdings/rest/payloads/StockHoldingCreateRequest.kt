package br.com.investlog.server.stockholdings.rest.payloads

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.PositiveOrZero
import java.math.BigDecimal
import java.util.UUID

data class StockHoldingCreateRequest(
    @field:NotNull val stockTypeId: UUID,
    @field:NotBlank
    @field:Pattern(regexp = "^[A-Za-z0-9]+$", message = "ticker must contain only letters and digits")
    val ticker: String,
    val name: String? = null,
    @field:PositiveOrZero val currentPrice: BigDecimal? = null,
    @field:Valid @field:NotNull val lot: LotCreateRequest,
)
