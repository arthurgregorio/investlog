package br.com.investlog.server.walletmoves.rest.payloads

import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import java.util.UUID

data class WalletMoveRequest(

    @field:NotNull(message = "A carteira de destino é obrigatória")
    val destinationWalletId: UUID?,

    @field:NotEmpty(message = "Selecione ao menos um investimento para mover")
    @field:Valid
    val items: List<WalletMoveItemRequest>?,
)
