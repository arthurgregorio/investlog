package br.com.investlog.server.strategies.rest.payloads

import jakarta.validation.constraints.NotNull
import java.util.UUID

data class HoldingClassRequest(
    @field:NotNull val strategyClassId: UUID,
)
