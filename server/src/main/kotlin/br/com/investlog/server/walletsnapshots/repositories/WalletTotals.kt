package br.com.investlog.server.walletsnapshots.repositories

import java.math.BigDecimal

data class WalletTotals(
    val walletId: Long,
    val currentValue: BigDecimal,
    val totalInvested: BigDecimal,
)
