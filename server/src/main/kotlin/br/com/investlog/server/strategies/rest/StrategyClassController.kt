package br.com.investlog.server.strategies.rest

import br.com.investlog.server.strategies.rest.payloads.ClassAssignmentRequest
import br.com.investlog.server.strategies.rest.payloads.ClassHoldingResponse
import br.com.investlog.server.strategies.rest.payloads.ClassTargetsRequest
import br.com.investlog.server.strategies.services.StrategyClassService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/strategy-classes")
class StrategyClassController(private val strategyClassService: StrategyClassService) {

    @GetMapping("/{id}/holdings")
    fun findHoldings(@PathVariable id: UUID): ResponseEntity<List<ClassHoldingResponse>> =
        ResponseEntity.ok(strategyClassService.findHoldings(id))

    @PostMapping("/{id}/holdings")
    fun assign(@PathVariable id: UUID, @Valid @RequestBody request: ClassAssignmentRequest): ResponseEntity<Void> {
        strategyClassService.assign(id, request)
        return ResponseEntity.noContent().build()
    }

    @PutMapping("/{id}/targets")
    fun updateTargets(
        @PathVariable id: UUID,
        @Valid @RequestBody request: ClassTargetsRequest,
    ): ResponseEntity<List<ClassHoldingResponse>> =
        ResponseEntity.ok(strategyClassService.updateTargets(id, request))
}
