package br.com.investlog.server.cryptoholdings.rest.payloads

import br.com.investlog.server.stockholdings.rest.payloads.LotCreateRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
import java.math.BigDecimal

data class CryptoHoldingCreateRequest(
    @field:Valid
    @field:NotNull
    val lot: LotCreateRequest,
    @field:NotBlank
    val ticker: String,
    val name: String?,
    @field:PositiveOrZero
    val currentPrice: BigDecimal?,
)
