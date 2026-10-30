package br.com.investlog.server.strategies.rest

import br.com.investlog.server.strategies.rest.payloads.AllocationGroupBy
import br.com.investlog.server.strategies.rest.payloads.OverviewAllocationResponse
import br.com.investlog.server.strategies.services.StrategyAllocationService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/overview/allocation")
class OverviewAllocationController(private val strategyAllocationService: StrategyAllocationService) {

    @GetMapping
    fun findAllocation(
        @RequestParam groupBy: AllocationGroupBy,
        @RequestParam(required = false) strategyId: UUID?,
    ): ResponseEntity<OverviewAllocationResponse> =
        ResponseEntity.ok(strategyAllocationService.findOverviewAllocation(groupBy, strategyId))
}
