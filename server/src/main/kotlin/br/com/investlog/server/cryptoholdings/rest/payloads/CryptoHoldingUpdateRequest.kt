package br.com.investlog.server.cryptoholdings.rest.payloads

import jakarta.validation.constraints.PositiveOrZero
import java.math.BigDecimal

data class CryptoHoldingUpdateRequest(
    val ticker: String? = null,
    val name: String? = null,
    @field:PositiveOrZero val currentPrice: BigDecimal? = null,
)
