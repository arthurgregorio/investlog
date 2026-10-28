package br.com.investlog.server.strategies.rest

import br.com.investlog.server.strategies.rest.payloads.StrategyClassesRequest
import br.com.investlog.server.strategies.rest.payloads.StrategyNameRequest
import br.com.investlog.server.strategies.rest.payloads.StrategyResponse
import br.com.investlog.server.strategies.services.StrategyService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/strategies")
class StrategyController(private val strategyService: StrategyService) {

    @GetMapping
    fun findAll(): ResponseEntity<List<StrategyResponse>> =
        ResponseEntity.ok(strategyService.findAll())

    @PostMapping
    fun create(@Valid @RequestBody request: StrategyNameRequest): ResponseEntity<StrategyResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(strategyService.create(request.name))

    @PatchMapping("/{id}")
    fun rename(@PathVariable id: UUID, @Valid @RequestBody request: StrategyNameRequest): ResponseEntity<StrategyResponse> =
        ResponseEntity.ok(strategyService.rename(id, request.name))

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        strategyService.delete(id)
        return ResponseEntity.noContent().build()
    }

    @PutMapping("/{id}/classes")
    fun replaceClasses(
        @PathVariable id: UUID,
        @Valid @RequestBody request: StrategyClassesRequest,
    ): ResponseEntity<StrategyResponse> =
        ResponseEntity.ok(strategyService.replaceClasses(id, request))
}
