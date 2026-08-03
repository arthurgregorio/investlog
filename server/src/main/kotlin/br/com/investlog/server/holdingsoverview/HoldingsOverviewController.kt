package br.com.investlog.server.holdingsoverview

import br.com.investlog.server.holdingsoverview.services.HoldingsOverviewService
import br.com.investlog.server.holdingsoverview.rest.payloads.HoldingRowResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/holdings")
class HoldingsOverviewController(private val holdingsOverviewService: HoldingsOverviewService) {

    @GetMapping
    fun findAll(
        @RequestParam(required = false) kind: String?,
        @RequestParam(required = false) typeLabel: String?,
        @RequestParam(required = false) walletId: UUID?,
        @RequestParam(required = false) search: String?,
        pageable: Pageable,
    ): PagedModel<HoldingRowResponse> = holdingsOverviewService.findAll(kind, typeLabel, walletId, search, pageable)
}
