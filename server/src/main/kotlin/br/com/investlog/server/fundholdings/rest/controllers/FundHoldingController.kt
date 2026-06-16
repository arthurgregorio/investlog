package br.com.investlog.server.fundholdings.rest.controllers

import br.com.investlog.server.fundholdings.domain.services.FundHoldingService
import br.com.investlog.server.fundholdings.rest.payloads.ContributionCreateRequest
import br.com.investlog.server.fundholdings.rest.payloads.ContributionResponse
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingCreateRequest
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingResponse
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingUpdateRequest
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/wallets/{walletId}/fund-holdings")
class FundHoldingController(private val service: FundHoldingService) {

    @GetMapping
    fun findAll(@PathVariable walletId: UUID, pageable: Pageable): PagedModel<FundHoldingResponse> =
        service.findAll(walletId, pageable)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@PathVariable walletId: UUID, @Valid @RequestBody request: FundHoldingCreateRequest): FundHoldingResponse =
        service.create(walletId, request)

    @PatchMapping("/{holdingId}")
    fun update(
        @PathVariable walletId: UUID,
        @PathVariable holdingId: UUID,
        @Valid @RequestBody request: FundHoldingUpdateRequest,
    ): FundHoldingResponse = service.update(walletId, holdingId, request)

    @DeleteMapping("/{holdingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable walletId: UUID, @PathVariable holdingId: UUID) =
        service.delete(walletId, holdingId)

    @PostMapping("/{holdingId}/contributions")
    @ResponseStatus(HttpStatus.CREATED)
    fun addContribution(
        @PathVariable walletId: UUID,
        @PathVariable holdingId: UUID,
        @Valid @RequestBody request: ContributionCreateRequest,
    ): ContributionResponse = service.addContribution(walletId, holdingId, request)

    @DeleteMapping("/{holdingId}/contributions/{contributionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteContribution(
        @PathVariable walletId: UUID,
        @PathVariable holdingId: UUID,
        @PathVariable contributionId: UUID,
    ) = service.deleteContribution(walletId, holdingId, contributionId)
}
