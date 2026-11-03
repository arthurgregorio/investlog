package br.com.investlog.server.strategies.rest.payloads

import java.math.BigDecimal

data class OverviewAllocationResponse(
    val groupBy: AllocationGroupBy,
    val displayCurrency: String,
    val totalValue: BigDecimal,
    val groups: List<AllocationGroupResponse>,
)
