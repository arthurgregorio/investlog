package br.com.investlog.server.stockholdings.rest.payloads

import br.com.investlog.server.results.rest.payloads.WithdrawalResponse
import java.math.BigDecimal
import java.util.UUID

data class StockHoldingResponse(
    val id: UUID,
    val walletId: UUID,
    val typeId: UUID,
    val typeName: String,
    val segmentId: UUID?,
    val segmentName: String?,
    val ticker: String,
    val name: String,
    val currentPrice: BigDecimal?,
    val frozen: Boolean,
    val strategyId: UUID,
    val strategyName: String,
    val strategyClassId: UUID,
    val strategyClassName: String,
    val lots: List<LotResponse>,
    val withdrawals: List<WithdrawalResponse>,
)
