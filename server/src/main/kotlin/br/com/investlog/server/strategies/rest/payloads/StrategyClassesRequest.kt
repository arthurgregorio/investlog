package br.com.investlog.server.strategies.rest.payloads

import jakarta.validation.Valid

data class StrategyClassesRequest(
    @field:Valid val classes: List<StrategyClassRequest>,
)
