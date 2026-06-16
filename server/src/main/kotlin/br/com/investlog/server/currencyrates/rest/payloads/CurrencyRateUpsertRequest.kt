package br.com.investlog.server.currencyrates.rest.payloads

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.DecimalMin
import java.math.BigDecimal

data class CurrencyRateUpsertRequest(
    @field:DecimalMin(value = "0", inclusive = false, message = "rate must be greater than 0")
    val rate: BigDecimal,
    @JsonProperty("isBase")
    val isBase: Boolean = false,
)
