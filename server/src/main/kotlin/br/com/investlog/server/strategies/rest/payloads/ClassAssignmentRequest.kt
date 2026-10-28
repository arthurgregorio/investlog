package br.com.investlog.server.strategies.rest.payloads

import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty

data class ClassAssignmentRequest(
    @field:NotEmpty @field:Valid val holdings: List<HoldingReferenceRequest>,
)
