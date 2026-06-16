package br.com.investlog.server.currencyrates.rest.controllers

import br.com.investlog.server.currencyrates.domain.services.CurrencyRateService
import br.com.investlog.server.currencyrates.rest.dtos.CurrencyRateResponse
import br.com.investlog.server.currencyrates.rest.dtos.CurrencyRateUpsertRequest
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/currency-rates")
class CurrencyRateController(private val currencyRateService: CurrencyRateService) {

    @GetMapping
    fun findAll(pageable: Pageable): PagedModel<CurrencyRateResponse> =
        currencyRateService.findAll(pageable)

    @PutMapping("/{currencyCode}")
    fun upsert(
        @PathVariable currencyCode: String,
        @Valid @RequestBody request: CurrencyRateUpsertRequest,
    ): CurrencyRateResponse =
        currencyRateService.upsert(currencyCode, request.rate, request.isBase)
}
