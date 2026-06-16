package br.com.investlog.server.stockholdings.rest.payloads

import java.math.BigDecimal
import java.util.UUID

data class StockHoldingResponse(
    val id: UUID,
    val walletId: UUID,
    val stockTypeId: UUID,
    val ticker: String,
    val name: String,
    val currentPrice: BigDecimal?,
    val lots: List<LotResponse>,
)
