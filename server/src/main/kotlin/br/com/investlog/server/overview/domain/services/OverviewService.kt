package br.com.investlog.server.overview.domain.services

import br.com.investlog.server.overview.domain.repositories.OverviewRepository
import br.com.investlog.server.overview.rest.payloads.PortfolioSummaryResponse
import br.com.investlog.server.overview.rest.payloads.SeriesPointResponse
import br.com.investlog.server.shared.security.CurrentUserProvider
import org.springframework.stereotype.Service

@Service
class OverviewService(
    private val currentUserProvider: CurrentUserProvider,
    private val overviewRepository: OverviewRepository,
) {

    fun getSummary(): PortfolioSummaryResponse {
        val user = currentUserProvider.getCurrentUser()
        return overviewRepository.findSummary(user.id, user.preferredCurrency)
    }

    fun getSeries(): List<SeriesPointResponse> {
        val user = currentUserProvider.getCurrentUser()
        return overviewRepository.findSeries(user.id, user.preferredCurrency)
    }
}
