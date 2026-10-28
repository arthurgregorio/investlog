package br.com.investlog.server.strategies.repositories

import java.math.BigDecimal
import java.util.UUID

data class StrategyClassRow(
    val id: Long,
    val externalId: UUID,
    val strategyId: Long,
    val name: String,
    val targetPercentage: BigDecimal?,
    val position: Int,
    val isDefault: Boolean,
)
