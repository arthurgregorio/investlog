package br.com.investlog.server.stockholdings.rest.payloads

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import java.math.BigDecimal
import java.time.LocalDate

data class LotCreateRequest(
    @field:NotNull val lotDate: LocalDate,
    @field:NotNull @field:Positive val quantity: BigDecimal,
    @field:NotNull @field:PositiveOrZero val price: BigDecimal,
)
