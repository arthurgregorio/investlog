package br.com.investlog.server.strategies.rest.payloads

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.util.UUID

data class StrategyClassRequest(
    val id: UUID? = null,
    @field:NotBlank @field:Size(max = 60) val name: String,
    @field:NotNull @field:DecimalMin("0") @field:DecimalMax("100") val targetPercentage: BigDecimal,
)
