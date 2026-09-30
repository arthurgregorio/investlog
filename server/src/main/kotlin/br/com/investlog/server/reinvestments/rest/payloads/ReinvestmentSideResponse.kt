package br.com.investlog.server.reinvestments.rest.payloads

import java.util.UUID

data class ReinvestmentSideResponse(
    val holdingId: UUID,
    val kind: String,
    val name: String,
    val ticker: String?,
    val walletId: UUID,
    val walletName: String,
)
