package br.com.investlog.server.overview.rest.payloads

import java.math.BigDecimal

data class MoverResponse(
    val kind: String,
    val ticker: String?,
    val name: String?,
    val changePct: BigDecimal,
)
