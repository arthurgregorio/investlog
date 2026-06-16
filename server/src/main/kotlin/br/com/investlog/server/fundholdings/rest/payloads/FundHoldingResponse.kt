package br.com.investlog.server.fundholdings.rest.payloads

import java.math.BigDecimal
import java.util.UUID

data class FundHoldingResponse(
    val id: UUID,
    val walletId: UUID,
    val fundTypeId: UUID,
    val name: String,
    val currentValue: BigDecimal?,
    val contributions: List<ContributionResponse>,
)
