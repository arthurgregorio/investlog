package br.com.investlog.server.cash.rest.payloads

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class CashTransferRequest(

    @field:NotNull(message = "A carteira de destino é obrigatória")
    val destinationWalletId: UUID?,

    @field:NotNull(message = "A data da transferência é obrigatória")
    val transferDate: LocalDate?,

    @field:NotNull(message = "O valor é obrigatório")
    @field:DecimalMin(value = "0", inclusive = false, message = "O valor deve ser maior que zero")
    val amount: BigDecimal?,

    @field:DecimalMin(value = "0", inclusive = false, message = "O câmbio deve ser maior que zero")
    val rate: BigDecimal? = null,
)
