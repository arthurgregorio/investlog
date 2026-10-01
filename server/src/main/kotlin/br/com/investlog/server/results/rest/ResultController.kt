package br.com.investlog.server.results.rest

import br.com.investlog.server.results.repositories.ResultRepository
import br.com.investlog.server.results.rest.payloads.ResultResponse
import br.com.investlog.server.results.rest.payloads.ResultSummaryResponse
import br.com.investlog.server.shared.security.CurrentUserProvider
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/results")
class ResultController(
    private val currentUserProvider: CurrentUserProvider,
    private val resultRepository: ResultRepository,
) {

    @GetMapping
    fun findAll(pageable: Pageable): ResponseEntity<PagedModel<ResultResponse>> {

        val userId = currentUserProvider.getCurrentUser().id

        return ResponseEntity.ok(resultRepository.findAll(userId, pageable))
    }

    @GetMapping("/summary")
    fun findSummary(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
    ): ResponseEntity<ResultSummaryResponse> {

        val user = currentUserProvider.getCurrentUser()

        return ResponseEntity.ok(resultRepository.findSummary(user.id, user.preferredCurrency, from, to))
    }
}
