package br.com.investlog.server.results.rest.payloads

import br.com.investlog.server.shared.validation.BoundedAmount
import br.com.investlog.server.shared.validation.BoundedUnitValue
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.LocalDate

data class HoldingWithdrawalRequest(

    @field:NotNull(message = "A data do resgate é obrigatória")
    val resultDate: LocalDate?,

    @field:NotNull(message = "A quantidade é obrigatória")
    @field:DecimalMin(value = "0", inclusive = false, message = "A quantidade deve ser maior que zero")
    @field:BoundedUnitValue
    val quantity: BigDecimal?,

    @field:NotNull(message = "O preço unitário é obrigatório")
    @field:DecimalMin(value = "0", message = "O preço unitário não pode ser negativo")
    @field:BoundedUnitValue
    val unitPrice: BigDecimal?,

    @field:DecimalMin(value = "0", message = "As taxas não podem ser negativas")
    @field:BoundedAmount
    val fees: BigDecimal? = null,

    @field:DecimalMin(value = "0", message = "Os impostos não podem ser negativos")
    @field:BoundedAmount
    val taxes: BigDecimal? = null,
)
