package br.com.investlog.server.reinvestments.rest

import br.com.investlog.server.reinvestments.repositories.ReinvestmentRepository
import br.com.investlog.server.reinvestments.rest.payloads.ReinvestmentRequest
import br.com.investlog.server.reinvestments.rest.payloads.ReinvestmentResponse
import br.com.investlog.server.reinvestments.services.ReinvestmentService
import br.com.investlog.server.shared.security.CurrentUserProvider
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/reinvestments")
class ReinvestmentController(
    private val currentUserProvider: CurrentUserProvider,
    private val reinvestmentService: ReinvestmentService,
    private val reinvestmentRepository: ReinvestmentRepository,
) {

    @PostMapping
    fun reinvest(@Valid @RequestBody request: ReinvestmentRequest): ResponseEntity<Void> {

        val userId = currentUserProvider.getCurrentUser().id
        reinvestmentService.reinvest(userId, request)

        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    @GetMapping
    fun findAll(pageable: Pageable): ResponseEntity<PagedModel<ReinvestmentResponse>> {

        val userId = currentUserProvider.getCurrentUser().id

        return ResponseEntity.ok(reinvestmentRepository.findAll(userId, pageable))
    }
}
