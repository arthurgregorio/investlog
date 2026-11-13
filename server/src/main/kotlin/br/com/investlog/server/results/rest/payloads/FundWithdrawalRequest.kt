package br.com.investlog.server.results.rest.payloads

import br.com.investlog.server.shared.validation.BoundedAmount
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.LocalDate

data class FundWithdrawalRequest(

    @field:NotNull(message = "A data do resgate é obrigatória")
    val resultDate: LocalDate?,

    @field:NotNull(message = "O valor do resgate é obrigatório")
    @field:DecimalMin(value = "0", inclusive = false, message = "O valor do resgate deve ser maior que zero")
    @field:BoundedAmount
    val amount: BigDecimal?,

    @field:DecimalMin(value = "0", message = "As taxas não podem ser negativas")
    @field:BoundedAmount
    val fees: BigDecimal? = null,

    @field:DecimalMin(value = "0", message = "Os impostos não podem ser negativos")
    @field:BoundedAmount
    val taxes: BigDecimal? = null,
)
