package br.com.investlog.server.strategies.rest.payloads

import java.math.BigDecimal
import java.util.UUID

data class ContributionPlanResponse(
    val strategyId: UUID,
    val displayCurrency: String,
    val amount: BigDecimal,
    val totalValueBefore: BigDecimal,
    val totalValueAfter: BigDecimal,
    val classes: List<ClassContributionResponse>,
    val leftOut: List<LeftOutHoldingResponse>,
)
