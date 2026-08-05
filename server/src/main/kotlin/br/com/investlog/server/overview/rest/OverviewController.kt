package br.com.investlog.server.overview.rest

import br.com.investlog.server.overview.services.OverviewService
import br.com.investlog.server.overview.rest.payloads.PortfolioSummaryResponse
import br.com.investlog.server.overview.rest.payloads.SeriesPointResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/overview")
class OverviewController(private val overviewService: OverviewService) {

    @GetMapping
    fun getSummary(): ResponseEntity<PortfolioSummaryResponse> = ResponseEntity.ok(overviewService.getSummary())

    @GetMapping("/series")
    fun getSeries(): ResponseEntity<List<SeriesPointResponse>> = ResponseEntity.ok(overviewService.getSeries())
}
