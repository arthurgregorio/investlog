package br.com.investlog.server.stockholdings.rest

import br.com.investlog.server.stockholdings.services.StockHoldingService
import br.com.investlog.server.stockholdings.rest.payloads.LotCreateRequest
import br.com.investlog.server.stockholdings.rest.payloads.LotResponse
import br.com.investlog.server.stockholdings.rest.payloads.LotUpdateRequest
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingCreateRequest
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingResponse
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingUpdateRequest
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
@RequestMapping("/wallets/{walletId}/stock-holdings")
class StockHoldingController(private val service: StockHoldingService) {

    @GetMapping
    fun findAll(@PathVariable walletId: UUID, pageable: Pageable): ResponseEntity<PagedModel<StockHoldingResponse>> =
        ResponseEntity.ok(service.findAll(walletId, pageable))

    @GetMapping("/{holdingId}")
    fun findById(@PathVariable walletId: UUID, @PathVariable holdingId: UUID): ResponseEntity<StockHoldingResponse> =
        ResponseEntity.ok(service.findById(walletId, holdingId))

    @PostMapping
    fun create(
        @PathVariable walletId: UUID,
        @Valid @RequestBody request: StockHoldingCreateRequest
    ): ResponseEntity<StockHoldingResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(service.create(walletId, request))

    @PatchMapping("/{holdingId}")
    fun update(
        @PathVariable walletId: UUID,
        @PathVariable holdingId: UUID,
        @Valid @RequestBody request: StockHoldingUpdateRequest,
    ): ResponseEntity<StockHoldingResponse> =
        ResponseEntity.ok(service.update(walletId, holdingId, request))

    @DeleteMapping("/{holdingId}")
    fun delete(@PathVariable walletId: UUID, @PathVariable holdingId: UUID): ResponseEntity<Void> {
        service.delete(walletId, holdingId)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{holdingId}/lots")
    fun addLot(
        @PathVariable walletId: UUID,
        @PathVariable holdingId: UUID,
        @Valid @RequestBody request: LotCreateRequest,
    ): ResponseEntity<LotResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(service.addLot(walletId, holdingId, request))

    @DeleteMapping("/{holdingId}/lots/{lotId}")
    fun deleteLot(
        @PathVariable walletId: UUID,
        @PathVariable holdingId: UUID,
        @PathVariable lotId: UUID,
    ): ResponseEntity<Void> {
        service.deleteLot(walletId, holdingId, lotId)
        return ResponseEntity.noContent().build()
    }

    @PatchMapping("/{holdingId}/lots/{lotId}")
    fun updateLotDate(
        @PathVariable walletId: UUID,
        @PathVariable holdingId: UUID,
        @PathVariable lotId: UUID,
        @Valid @RequestBody request: LotUpdateRequest,
    ): ResponseEntity<LotResponse> =
        ResponseEntity.ok(service.updateLotDate(walletId, holdingId, lotId, request))
}
