package br.com.investlog.server.cryptoholdings.rest.payloads

import br.com.investlog.server.stockholdings.rest.payloads.LotResponse
import java.math.BigDecimal
import java.util.UUID

data class CryptoHoldingResponse(
    val id: UUID,
    val walletId: UUID,
    val ticker: String,
    val name: String,
    val currentPrice: BigDecimal?,
    val lots: List<LotResponse>,
)
