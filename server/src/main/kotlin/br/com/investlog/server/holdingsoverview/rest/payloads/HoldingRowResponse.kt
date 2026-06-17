package br.com.investlog.server.holdingsoverview.rest.payloads

import java.math.BigDecimal
import java.util.UUID

data class HoldingRowResponse(
    val id: UUID,
    val kind: String,
    val name: String,
    val ticker: String?,
    val typeLabel: String?,
    val walletId: UUID,
    val walletName: String,
    val walletCurrency: String,
    val quantity: BigDecimal?,
    val costBasis: BigDecimal,
    val currentValue: BigDecimal?,
    val gain: BigDecimal?,
    val gainPct: BigDecimal?,
)
