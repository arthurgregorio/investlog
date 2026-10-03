package br.com.investlog.server.reinvestments.repositories

import br.com.investlog.server.results.repositories.HoldingPosition
import java.math.BigDecimal

data class ReinvestmentHolding(
    val position: HoldingPosition,
    val name: String,
    val walletCurrency: String,
    val currentPrice: BigDecimal?,
    val frozen: Boolean,
)
