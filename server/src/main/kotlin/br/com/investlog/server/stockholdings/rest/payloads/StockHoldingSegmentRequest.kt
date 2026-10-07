package br.com.investlog.server.stockholdings.rest.payloads

import java.util.UUID

data class StockHoldingSegmentRequest(
    val stockSegmentId: UUID?,
)
