package br.com.investlog.server.walletmoves.repositories

import br.com.investlog.server.jooq.finances.enums.HoldingStatus
import br.com.investlog.server.jooq.finances.enums.WalletKind
import java.math.BigDecimal
import java.util.UUID

data class MovableHolding(
    val id: Long,
    val externalId: UUID,
    val kind: WalletKind,
    val status: HoldingStatus,
    val name: String,
    val ticker: String?,
    val typeId: Long?,
    val currentPrice: BigDecimal?,
    val currentValue: BigDecimal?,
    val quantity: BigDecimal?,
    val costBasis: BigDecimal,
    val hasResults: Boolean,
) {
    val reference: HoldingReference
        get() = HoldingReference(id, externalId)
}
