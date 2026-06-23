package br.com.investlog.server.stockholdings.rest.payloads

import jakarta.validation.constraints.NotNull
import java.time.LocalDate

data class LotUpdateRequest(
    @field:NotNull val lotDate: LocalDate,
)
