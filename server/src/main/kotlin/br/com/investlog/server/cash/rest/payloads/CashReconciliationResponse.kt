package br.com.investlog.server.cash.rest.payloads

import java.math.BigDecimal
import java.util.UUID

data class CashReconciliationResponse(
    val walletId: UUID,
    val walletName: String,
    val kind: String,
    val currency: String,
    val cash: BigDecimal,
    val sources: List<CashSourceResponse>,
    val suggestion: CashSuggestionResponse?,
)
