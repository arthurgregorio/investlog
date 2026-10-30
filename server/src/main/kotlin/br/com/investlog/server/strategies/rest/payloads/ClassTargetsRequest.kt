package br.com.investlog.server.strategies.rest.payloads

import jakarta.validation.Valid

data class ClassTargetsRequest(
    @field:Valid val targets: List<HoldingTargetRequest>,
)
