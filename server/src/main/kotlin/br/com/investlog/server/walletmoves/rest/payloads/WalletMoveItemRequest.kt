package br.com.investlog.server.walletmoves.rest.payloads

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.util.UUID

data class WalletMoveItemRequest(

    @field:NotNull(message = "O investimento é obrigatório")
    val holdingId: UUID?,

    @field:DecimalMin(value = "0", inclusive = false, message = "A quantidade deve ser maior que zero")
    val quantity: BigDecimal? = null,
)
