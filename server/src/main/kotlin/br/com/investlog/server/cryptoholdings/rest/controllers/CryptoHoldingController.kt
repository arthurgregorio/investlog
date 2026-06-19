package br.com.investlog.server.cryptoholdings.rest.controllers

import br.com.investlog.server.cryptoholdings.domain.services.CryptoHoldingService
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingCreateRequest
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingResponse
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingUpdateRequest
import br.com.investlog.server.stockholdings.rest.payloads.LotCreateRequest
import br.com.investlog.server.stockholdings.rest.payloads.LotResponse
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
@RequestMapping("/wallets/{walletId}/crypto-holdings")
class CryptoHoldingController(
    private val service: CryptoHoldingService
) {

    @GetMapping
    fun findAll(@PathVariable walletId: UUID, pageable: Pageable): PagedModel<CryptoHoldingResponse> =
        service.findAll(walletId, pageable)

    @GetMapping("/{holdingId}")
    fun findById(@PathVariable walletId: UUID, @PathVariable holdingId: UUID): CryptoHoldingResponse =
        service.findById(walletId, holdingId)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @PathVariable walletId: UUID,
        @Valid @RequestBody request: CryptoHoldingCreateRequest
    ): CryptoHoldingResponse =
        service.create(walletId, request)

    @PatchMapping("/{holdingId}")
    fun update(
        @PathVariable walletId: UUID,
        @PathVariable holdingId: UUID,
        @Valid @RequestBody request: CryptoHoldingUpdateRequest,
    ): CryptoHoldingResponse = service.update(walletId, holdingId, request)

    @DeleteMapping("/{holdingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable walletId: UUID, @PathVariable holdingId: UUID) =
        service.delete(walletId, holdingId)

    @PostMapping("/{holdingId}/lots")
    @ResponseStatus(HttpStatus.CREATED)
    fun addLot(
        @PathVariable walletId: UUID,
        @PathVariable holdingId: UUID,
        @Valid @RequestBody request: LotCreateRequest,
    ): LotResponse = service.addLot(walletId, holdingId, request)

    @DeleteMapping("/{holdingId}/lots/{lotId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteLot(
        @PathVariable walletId: UUID,
        @PathVariable holdingId: UUID,
        @PathVariable lotId: UUID,
    ) = service.deleteLot(walletId, holdingId, lotId)
}
