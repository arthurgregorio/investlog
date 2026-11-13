package br.com.investlog.server.currencyrates.rest.payloads

import br.com.investlog.server.shared.validation.BoundedUnitValue
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.DecimalMin
import java.math.BigDecimal

data class CurrencyRateUpsertRequest(
    @field:DecimalMin(value = "0", inclusive = false, message = "rate deve ser maior que 0")
    @field:BoundedUnitValue
    val rate: BigDecimal,
    @JsonProperty("isBase")
    val isBase: Boolean = false,
)
