package br.com.investlog.server.stockholdings.rest.payloads

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import java.math.BigDecimal
import java.time.LocalDate

data class LotUpdateRequest(
    @field:NotNull val lotDate: LocalDate,
    @field:Positive val quantity: BigDecimal? = null,
    @field:PositiveOrZero val price: BigDecimal? = null,
)
