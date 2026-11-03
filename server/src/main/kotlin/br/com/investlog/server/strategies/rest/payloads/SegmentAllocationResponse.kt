package br.com.investlog.server.strategies.rest.payloads

import java.math.BigDecimal

data class SegmentAllocationResponse(
    val label: String,
    val value: BigDecimal,
    val classPercentage: BigDecimal,
    val strategyPercentage: BigDecimal,
)
