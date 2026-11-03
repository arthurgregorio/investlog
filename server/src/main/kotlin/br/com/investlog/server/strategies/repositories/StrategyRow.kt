package br.com.investlog.server.strategies.repositories

import java.util.UUID

data class StrategyRow(
    val id: Long,
    val externalId: UUID,
    val name: String,
    val isDefault: Boolean,
)
