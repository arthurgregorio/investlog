package br.com.investlog.server.currencyrates.rest.dtos

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

data class CurrencyRateResponse(
    val currencyCode: String,
    val rate: BigDecimal,
    @get:JsonProperty("isBase")
    val isBase: Boolean,
)
