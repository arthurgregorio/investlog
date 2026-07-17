package br.com.investlog.server.cryptoholdings.rest.payloads

import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.PositiveOrZero
import java.math.BigDecimal

data class CryptoHoldingUpdateRequest(
    @field:Pattern(regexp = "^[A-Za-z0-9]+$", message = "ticker must contain only letters and digits")
    val ticker: String? = null,
    val name: String? = null,
    @field:PositiveOrZero val currentPrice: BigDecimal? = null,
)
