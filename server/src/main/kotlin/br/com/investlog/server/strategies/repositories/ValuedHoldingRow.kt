package br.com.investlog.server.strategies.repositories

import br.com.investlog.server.jooq.finances.enums.WalletKind
import java.math.BigDecimal
import java.util.UUID

data class ValuedHoldingRow(
    val kind: WalletKind,
    val externalId: UUID,
    val walletExternalId: UUID,
    val walletName: String,
    val ticker: String?,
    val name: String,
    val typeLabel: String?,
    val segmentLabel: String?,
    val frozen: Boolean,
    val strategyClassId: Long,
    val targetPercentage: BigDecimal?,
    val value: BigDecimal,
)
