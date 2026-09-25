package br.com.investlog.server.walletmoves.repositories

import java.math.BigDecimal

data class HoldingContribution(
    val id: Long,
    val amount: BigDecimal,
)
