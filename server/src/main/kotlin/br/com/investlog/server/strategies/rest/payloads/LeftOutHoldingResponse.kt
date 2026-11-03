package br.com.investlog.server.strategies.rest.payloads

import java.util.UUID

data class LeftOutHoldingResponse(
    val kind: String,
    val id: UUID,
    val walletId: UUID,
    val walletName: String,
    val ticker: String?,
    val name: String,
    val className: String,
    val reason: LeftOutReason,
)
