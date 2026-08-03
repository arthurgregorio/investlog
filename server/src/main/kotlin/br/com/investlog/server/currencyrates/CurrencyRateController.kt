package br.com.investlog.server.currencyrates

import br.com.investlog.server.currencyrates.services.CurrencyRateService
import br.com.investlog.server.currencyrates.rest.payloads.CurrencyRateResponse
import br.com.investlog.server.currencyrates.rest.payloads.CurrencyRateUpsertRequest
import br.com.investlog.server.shared.rest.payloads.CurrencyCode
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping("/currency-rates")
class CurrencyRateController(
    private val currencyRateService: CurrencyRateService
) {

    @GetMapping
    fun findAll(pageable: Pageable): PagedModel<CurrencyRateResponse> = currencyRateService.findAll(pageable)

    @PutMapping("/{currencyCode}")
    fun upsert(
        @PathVariable currencyCode: CurrencyCode, @Valid @RequestBody request: CurrencyRateUpsertRequest,
    ): CurrencyRateResponse =
        currencyRateService.upsert(currencyCode, request.rate, request.isBase)
}
