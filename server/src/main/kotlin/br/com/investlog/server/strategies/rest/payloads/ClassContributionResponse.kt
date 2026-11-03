package br.com.investlog.server.strategies.rest.payloads

import java.math.BigDecimal
import java.util.UUID

data class ClassContributionResponse(
    val id: UUID,
    val name: String,
    val targetPercentage: BigDecimal,
    val percentageBefore: BigDecimal,
    val percentageAfter: BigDecimal,
    val amount: BigDecimal,
    val holdings: List<HoldingContributionResponse>,
)
