package br.com.investlog.server.cryptoholdings.rest.payloads

import br.com.investlog.server.shared.validation.BoundedUnitValue
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.util.UUID

data class CryptoHoldingUpdateRequest(
    val typeId: UUID? = null,
    @field:Size(max = 20, message = "ticker deve ter no máximo 20 caracteres")
    @field:Pattern(regexp = "^[A-Za-z0-9]+$", message = "ticker deve conter apenas letras e números")
    val ticker: String? = null,
    val name: String? = null,
    @field:PositiveOrZero
    @field:BoundedUnitValue
    val currentPrice: BigDecimal? = null,
    val frozen: Boolean? = null,
)
