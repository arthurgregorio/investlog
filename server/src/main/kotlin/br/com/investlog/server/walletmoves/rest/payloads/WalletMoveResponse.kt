package br.com.investlog.server.walletmoves.rest.payloads

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class WalletMoveResponse(
    val id: UUID,
    val movedAt: LocalDate,
    val direction: WalletMoveDirection,
    val kind: String,
    val holdingName: String,
    val ticker: String?,
    val quantity: BigDecimal?,
    val originWalletId: UUID?,
    val originWalletName: String?,
    val destinationWalletId: UUID?,
    val destinationWalletName: String?,
)
