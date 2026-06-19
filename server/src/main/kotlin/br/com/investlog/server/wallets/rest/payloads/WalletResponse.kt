package br.com.investlog.server.wallets.rest.payloads

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class WalletResponse(
    val id: UUID,
    val name: String,
    val kind: WalletKind,
    val currency: String,
    val holdingCount: Int,
    val totalInvested: BigDecimal,
    val createdAt: OffsetDateTime,
)
