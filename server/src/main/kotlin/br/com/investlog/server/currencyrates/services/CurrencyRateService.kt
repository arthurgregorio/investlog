package br.com.investlog.server.currencyrates.services

import br.com.investlog.server.currencyrates.repositories.CurrencyRateRepository
import br.com.investlog.server.currencyrates.rest.payloads.CurrencyRateResponse
import br.com.investlog.server.shared.rest.payloads.CurrencyCode
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class CurrencyRateService(
    private val currencyRateRepository: CurrencyRateRepository,
) {

    fun findAll(pageable: Pageable): PagedModel<CurrencyRateResponse> = currencyRateRepository.findAll(pageable)

    @Transactional
    fun upsert(currencyCode: CurrencyCode, rate: BigDecimal, isBase: Boolean): CurrencyRateResponse =
        currencyRateRepository.upsert(currencyCode.text, rate, isBase)
}
