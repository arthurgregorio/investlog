package br.com.investlog.server.cryptoholdings.rest.payloads

import br.com.investlog.server.shared.validation.BoundedUnitValue
import br.com.investlog.server.stockholdings.rest.payloads.LotCreateRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.util.UUID

data class CryptoHoldingCreateRequest(
    val typeId: UUID? = null,
    val segmentId: UUID? = null,
    val strategyClassId: UUID? = null,
    @field:Valid
    @field:NotNull
    val lot: LotCreateRequest,
    @field:NotBlank
    @field:Size(max = 20, message = "ticker deve ter no máximo 20 caracteres")
    @field:Pattern(regexp = "^[A-Za-z0-9]+$", message = "ticker deve conter apenas letras e números")
    val ticker: String,
    val name: String?,
    @field:PositiveOrZero
    @field:BoundedUnitValue
    val currentPrice: BigDecimal?,
)
