package br.com.investlog.server.strategies.rest.payloads

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class StrategyNameRequest(
    @field:NotBlank @field:Size(max = 60) val name: String,
)
