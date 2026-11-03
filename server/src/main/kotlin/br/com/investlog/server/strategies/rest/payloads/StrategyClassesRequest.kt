package br.com.investlog.server.strategies.rest.payloads

import jakarta.validation.Valid

data class StrategyClassesRequest(
    val classes: List<@Valid StrategyClassRequest>,
)
