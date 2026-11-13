package br.com.investlog.server.stockholdings.rest.payloads

import br.com.investlog.server.shared.validation.BoundedUnitValue
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import java.math.BigDecimal
import java.time.LocalDate

data class LotUpdateRequest(
    @field:NotNull val lotDate: LocalDate,
    @field:Positive @field:BoundedUnitValue val quantity: BigDecimal? = null,
    @field:PositiveOrZero @field:BoundedUnitValue val price: BigDecimal? = null,
)
