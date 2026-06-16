package br.com.investlog.server.currencyrates.rest.controllers

import br.com.investlog.server.currencyrates.domain.services.CurrencyRateService
import br.com.investlog.server.currencyrates.rest.dtos.CurrencyRateResponse
import br.com.investlog.server.currencyrates.rest.dtos.CurrencyRateUpsertRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.Pattern
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
class CurrencyRateController(private val currencyRateService: CurrencyRateService) {

    @GetMapping
    fun findAll(pageable: Pageable): PagedModel<CurrencyRateResponse> =
        currencyRateService.findAll(pageable)

    @PutMapping("/{currencyCode}")
    fun upsert(
        @Pattern(regexp = "[A-Z]{2,10}", message = "currencyCode must be 2–10 uppercase letters") @PathVariable currencyCode: String,
        @Valid @RequestBody request: CurrencyRateUpsertRequest,
    ): CurrencyRateResponse =
        currencyRateService.upsert(currencyCode, request.rate, request.isBase)
}
