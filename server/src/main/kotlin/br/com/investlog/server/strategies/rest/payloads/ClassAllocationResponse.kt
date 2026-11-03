package br.com.investlog.server.strategies.rest.payloads

import java.math.BigDecimal
import java.util.UUID

data class ClassAllocationResponse(
    val id: UUID,
    val name: String,
    val targetPercentage: BigDecimal,
    val actualPercentage: BigDecimal,
    val driftPercentagePoints: BigDecimal,
    val driftValue: BigDecimal,
    val value: BigDecimal,
    val holdings: List<HoldingAllocationResponse>,
    val segments: List<SegmentAllocationResponse>,
)
