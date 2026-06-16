package br.com.investlog.server.currencyrates.domain.services

import br.com.investlog.server.currencyrates.domain.repositories.CurrencyRateRepository
import br.com.investlog.server.currencyrates.rest.dtos.CurrencyRateResponse
import br.com.investlog.server.shared.security.CurrentUserProvider
import java.math.BigDecimal
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Service

@Service
class CurrencyRateService(
    private val currentUserProvider: CurrentUserProvider,
    private val currencyRateRepository: CurrencyRateRepository,
) {

    fun findAll(pageable: Pageable): PagedModel<CurrencyRateResponse> {
        val userId = currentUserProvider.getCurrentUser().id

        return currencyRateRepository.findAll(userId, pageable)
    }

    fun upsert(currencyCode: String, rate: BigDecimal, isBase: Boolean): CurrencyRateResponse {
        val userId = currentUserProvider.getCurrentUser().id

        return currencyRateRepository.upsert(userId, currencyCode, rate, isBase)
    }
}
