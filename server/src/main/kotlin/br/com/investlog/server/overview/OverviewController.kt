package br.com.investlog.server.overview

import br.com.investlog.server.overview.services.OverviewService
import br.com.investlog.server.overview.rest.payloads.PortfolioSummaryResponse
import br.com.investlog.server.overview.rest.payloads.SeriesPointResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/overview")
class OverviewController(private val overviewService: OverviewService) {

    @GetMapping
    fun getSummary(): PortfolioSummaryResponse = overviewService.getSummary()

    @GetMapping("/series")
    fun getSeries(): List<SeriesPointResponse> = overviewService.getSeries()
}
