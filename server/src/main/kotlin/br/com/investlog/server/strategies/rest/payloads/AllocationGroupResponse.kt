package br.com.investlog.server.strategies.rest.payloads

import java.math.BigDecimal
import java.util.UUID

data class AllocationGroupResponse(
    val id: UUID?,
    val label: String,
    val holdingCount: Int,
    val value: BigDecimal,
    val percentage: BigDecimal,
)
