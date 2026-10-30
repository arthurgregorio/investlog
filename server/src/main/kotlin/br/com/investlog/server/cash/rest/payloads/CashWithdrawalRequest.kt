package br.com.investlog.server.cash.rest.payloads

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDate

data class CashWithdrawalRequest(

    @field:NotNull(message = "A data do saque é obrigatória")
    val withdrawalDate: LocalDate?,

    @field:NotNull(message = "O valor é obrigatório")
    @field:DecimalMin(value = "0", inclusive = false, message = "O valor deve ser maior que zero")
    val amount: BigDecimal?,

    @field:Size(max = 60, message = "O nome do banco deve ter no máximo 60 caracteres")
    val bankName: String? = null,
)
