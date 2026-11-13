package br.com.investlog.server.reinvestments.rest.payloads

import br.com.investlog.server.jooq.finances.enums.WalletKind
import br.com.investlog.server.shared.validation.BoundedAmount
import br.com.investlog.server.shared.validation.BoundedUnitValue
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class ReinvestmentRequest(

    @field:NotNull(message = "O tipo do investimento de origem é obrigatório")
    val sourceKind: WalletKind?,

    @field:NotNull(message = "O investimento de origem é obrigatório")
    val sourceHoldingId: UUID?,

    @field:NotNull(message = "O tipo do investimento de destino é obrigatório")
    val destinationKind: WalletKind?,

    @field:NotNull(message = "O investimento de destino é obrigatório")
    val destinationHoldingId: UUID?,

    @field:NotNull(message = "A data do reinvestimento é obrigatória")
    val reinvestmentDate: LocalDate?,

    @field:DecimalMin(value = "0", inclusive = false, message = "A quantidade deve ser maior que zero")
    @field:BoundedUnitValue
    val quantity: BigDecimal? = null,

    @field:DecimalMin(value = "0", inclusive = false, message = "O preço unitário deve ser maior que zero")
    @field:BoundedUnitValue
    val unitPrice: BigDecimal? = null,

    @field:DecimalMin(value = "0", inclusive = false, message = "O valor deve ser maior que zero")
    @field:BoundedAmount
    val amount: BigDecimal? = null,

    @field:DecimalMin(value = "0", message = "As taxas não podem ser negativas")
    @field:BoundedAmount
    val fees: BigDecimal? = null,

    @field:DecimalMin(value = "0", message = "Os impostos não podem ser negativos")
    @field:BoundedAmount
    val taxes: BigDecimal? = null,
)
