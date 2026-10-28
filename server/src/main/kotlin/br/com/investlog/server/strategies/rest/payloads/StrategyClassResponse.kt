package br.com.investlog.server.strategies.rest.payloads

import java.math.BigDecimal
import java.util.UUID

data class StrategyClassResponse(
    val id: UUID,
    val name: String,
    val targetPercentage: BigDecimal?,
    val position: Int,
    val default: Boolean,
    val holdingCount: Int,
    val typedCount: Int,
    val typedPercentageSum: BigDecimal,
    val blankSharePercentage: BigDecimal?,
)
