package br.com.investlog.server.walletmoves.repositories

import java.math.BigDecimal

data class HoldingLot(
    val id: Long,
    val quantity: BigDecimal,
    val price: BigDecimal,
)
