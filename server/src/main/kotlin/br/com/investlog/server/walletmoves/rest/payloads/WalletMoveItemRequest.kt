package br.com.investlog.server.walletmoves.rest.payloads

import br.com.investlog.server.shared.validation.BoundedUnitValue
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.util.UUID

data class WalletMoveItemRequest(

    @field:NotNull(message = "O investimento é obrigatório")
    val holdingId: UUID?,

    @field:DecimalMin(value = "0", inclusive = false, message = "A quantidade deve ser maior que zero")
    @field:BoundedUnitValue
    val quantity: BigDecimal? = null,
)
