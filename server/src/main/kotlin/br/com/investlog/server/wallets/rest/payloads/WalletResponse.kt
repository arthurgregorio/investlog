package br.com.investlog.server.wallets.rest.payloads

import java.time.OffsetDateTime
import java.util.UUID

data class WalletResponse(
    val id: UUID,
    val name: String,
    val kind: String,
    val currency: String,
    val createdAt: OffsetDateTime,
)
