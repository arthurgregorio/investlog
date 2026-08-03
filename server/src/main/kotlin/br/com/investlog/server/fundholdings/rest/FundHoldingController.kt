package br.com.investlog.server.fundholdings.rest

import br.com.investlog.server.fundholdings.services.FundHoldingService
import br.com.investlog.server.fundholdings.rest.payloads.ContributionCreateRequest
import br.com.investlog.server.fundholdings.rest.payloads.ContributionResponse
import br.com.investlog.server.fundholdings.rest.payloads.ContributionUpdateRequest
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingCreateRequest
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingResponse
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingUpdateRequest
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/wallets/{walletId}/fund-holdings")
class FundHoldingController(
    private val service: FundHoldingService
) {

    @GetMapping
    fun findAll(@PathVariable walletId: UUID, pageable: Pageable): ResponseEntity<PagedModel<FundHoldingResponse>> =
        ResponseEntity.ok(service.findAll(walletId, pageable))

    @GetMapping("/{holdingId}")
    fun findById(@PathVariable walletId: UUID, @PathVariable holdingId: UUID): ResponseEntity<FundHoldingResponse> =
        ResponseEntity.ok(service.findById(walletId, holdingId))

    @PostMapping
    fun create(
        @PathVariable walletId: UUID,
        @Valid @RequestBody request: FundHoldingCreateRequest
    ): ResponseEntity<FundHoldingResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(service.create(walletId, request))

    @PatchMapping("/{holdingId}")
    fun update(
        @PathVariable walletId: UUID,
        @PathVariable holdingId: UUID,
        @Valid @RequestBody request: FundHoldingUpdateRequest,
    ): ResponseEntity<FundHoldingResponse> =
        ResponseEntity.ok(service.update(walletId, holdingId, request))

    @DeleteMapping("/{holdingId}")
    fun delete(@PathVariable walletId: UUID, @PathVariable holdingId: UUID): ResponseEntity<Void> {
        service.delete(walletId, holdingId)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{holdingId}/contributions")
    fun addContribution(
        @PathVariable walletId: UUID,
        @PathVariable holdingId: UUID,
        @Valid @RequestBody request: ContributionCreateRequest,
    ): ResponseEntity<ContributionResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(service.addContribution(walletId, holdingId, request))

    @DeleteMapping("/{holdingId}/contributions/{contributionId}")
    fun deleteContribution(
        @PathVariable walletId: UUID,
        @PathVariable holdingId: UUID,
        @PathVariable contributionId: UUID,
    ): ResponseEntity<Void> {
        service.deleteContribution(walletId, holdingId, contributionId)
        return ResponseEntity.noContent().build()
    }

    @PatchMapping("/{holdingId}/contributions/{contributionId}")
    fun updateContributionDate(
        @PathVariable walletId: UUID,
        @PathVariable holdingId: UUID,
        @PathVariable contributionId: UUID,
        @Valid @RequestBody request: ContributionUpdateRequest,
    ): ResponseEntity<ContributionResponse> =
        ResponseEntity.ok(service.updateContributionDate(walletId, holdingId, contributionId, request))
}
