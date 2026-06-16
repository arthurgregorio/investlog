package br.com.investlog.server.stockholdings.rest.payloads

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class LotResponse(
    val id: UUID,
    val lotDate: LocalDate,
    val quantity: BigDecimal,
    val price: BigDecimal,
)
