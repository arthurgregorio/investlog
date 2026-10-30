package br.com.investlog.server.strategies.repositories

import br.com.investlog.server.jooq.finances.enums.WalletKind
import java.math.BigDecimal
import java.util.UUID

data class HoldingTargetRow(
    val kind: WalletKind,
    val holdingId: Long,
    val holdingExternalId: UUID,
    val strategyClassId: Long,
    val targetPercentage: BigDecimal?,
)
