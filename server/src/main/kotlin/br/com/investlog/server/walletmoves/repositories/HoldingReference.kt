package br.com.investlog.server.walletmoves.repositories

import java.util.UUID

data class HoldingReference(
    val id: Long,
    val externalId: UUID,
)
