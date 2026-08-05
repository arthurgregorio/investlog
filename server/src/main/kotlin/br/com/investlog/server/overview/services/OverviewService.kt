package br.com.investlog.server.overview.services

import br.com.investlog.server.overview.repositories.OverviewRepository
import br.com.investlog.server.overview.rest.payloads.PortfolioSummaryResponse
import br.com.investlog.server.overview.rest.payloads.SeriesPointResponse
import br.com.investlog.server.shared.security.CurrentUserProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
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
