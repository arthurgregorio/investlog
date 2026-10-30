package br.com.investlog.server.strategies.rest.payloads

import java.util.UUID

data class StrategyResponse(
    val id: UUID,
    val name: String,
    val default: Boolean,
    val holdingCount: Int,
    val classes: List<StrategyClassResponse>,
)
