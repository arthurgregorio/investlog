package br.com.investlog.server.results.repositories

import br.com.investlog.server.jooq.finances.enums.HoldingStatus
import br.com.investlog.server.jooq.finances.enums.WalletKind
import java.math.BigDecimal

data class HoldingPosition(
    val holdingId: Long,
    val kind: WalletKind,
    val status: HoldingStatus,
    val quantity: BigDecimal?,
    val costBasis: BigDecimal,
    val currentValue: BigDecimal?,
)
