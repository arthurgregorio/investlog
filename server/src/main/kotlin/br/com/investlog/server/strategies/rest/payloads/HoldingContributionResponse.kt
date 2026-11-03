package br.com.investlog.server.strategies.rest.payloads

import java.math.BigDecimal
import java.util.UUID

data class HoldingContributionResponse(
    val kind: String,
    val id: UUID,
    val walletId: UUID,
    val walletName: String,
    val ticker: String?,
    val name: String,
    val amount: BigDecimal,
)
