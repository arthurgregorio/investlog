package br.com.investlog.server.walletdetail.repositories

import java.math.BigDecimal
import java.util.UUID

data class WalletHolding(
    val id: UUID,
    val name: String,
    val ticker: String?,
    val kind: String,
    val costBasis: BigDecimal,
    val currentValue: BigDecimal?,
)
