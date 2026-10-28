package br.com.investlog.server.strategies.rest.payloads

import java.math.BigDecimal
import java.util.UUID

data class StrategyAllocationResponse(
    val strategyId: UUID,
    val strategyName: String,
    val displayCurrency: String,
    val totalValue: BigDecimal,
    val classes: List<ClassAllocationResponse>,
    val unassignedHoldingCount: Int,
    val unassignedValue: BigDecimal,
)
