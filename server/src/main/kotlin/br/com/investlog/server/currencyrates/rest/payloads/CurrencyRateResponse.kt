package br.com.investlog.server.currencyrates.rest.payloads

import br.com.investlog.server.shared.rest.payloads.CurrencyCode
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

data class CurrencyRateResponse(
    val currencyCode: CurrencyCode,
    val rate: BigDecimal,
    @get:JsonProperty("isBase")
    val isBase: Boolean,
)
