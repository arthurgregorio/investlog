package br.com.investlog.server.strategies.rest.payloads

import jakarta.validation.Valid

data class ClassTargetsRequest(
    val targets: List<@Valid HoldingTargetRequest>,
)
