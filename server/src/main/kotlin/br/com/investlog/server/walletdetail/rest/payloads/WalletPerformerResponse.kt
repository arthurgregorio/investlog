package br.com.investlog.server.walletdetail.rest.payloads

import java.math.BigDecimal
import java.util.UUID

data class WalletPerformerResponse(
    val id: UUID,
    val name: String,
    val ticker: String?,
    val kind: String,
    val gain: BigDecimal,
    val gainPct: BigDecimal,
)
