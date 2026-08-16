package br.com.investlog.server.overview.rest

import br.com.investlog.server.overview.repositories.OverviewRepository
import br.com.investlog.server.overview.rest.payloads.PortfolioSummaryResponse
import br.com.investlog.server.overview.rest.payloads.SeriesPointResponse
import br.com.investlog.server.shared.security.CurrentUserProvider
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/overview")
class OverviewController(
    private val currentUserProvider: CurrentUserProvider,
    private val overviewRepository: OverviewRepository,
) {

    @GetMapping
    fun getSummary(): ResponseEntity<PortfolioSummaryResponse> {
        val user = currentUserProvider.getCurrentUser()
        return ResponseEntity.ok(overviewRepository.findSummary(user.id, user.preferredCurrency))
    }

    @GetMapping("/series")
    fun getSeries(): ResponseEntity<List<SeriesPointResponse>> {
        val user = currentUserProvider.getCurrentUser()
        return ResponseEntity.ok(overviewRepository.findSeries(user.id, user.preferredCurrency))
    }
}
