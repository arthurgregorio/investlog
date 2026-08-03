package br.com.investlog.server.stockholdings

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
@RequestMapping("/wallets/{walletId}/stock-holdings")
class StockHoldingController(private val service: StockHoldingService) {

    @GetMapping
    fun findAll(@PathVariable walletId: UUID, pageable: Pageable): PagedModel<StockHoldingResponse> =
        service.findAll(walletId, pageable)

    @GetMapping("/{holdingId}")
    fun findById(@PathVariable walletId: UUID, @PathVariable holdingId: UUID): StockHoldingResponse =
        service.findById(walletId, holdingId)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @PathVariable walletId: UUID,
        @Valid @RequestBody request: StockHoldingCreateRequest
    ): StockHoldingResponse = service.create(walletId, request)

    @PatchMapping("/{holdingId}")
    fun update(
        @PathVariable walletId: UUID,
        @PathVariable holdingId: UUID,
        @Valid @RequestBody request: StockHoldingUpdateRequest,
    ): StockHoldingResponse = service.update(walletId, holdingId, request)

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

    @PatchMapping("/{holdingId}/lots/{lotId}")
    fun updateLotDate(
        @PathVariable walletId: UUID,
        @PathVariable holdingId: UUID,
        @PathVariable lotId: UUID,
        @Valid @RequestBody request: LotUpdateRequest,
    ): LotResponse = service.updateLotDate(walletId, holdingId, lotId, request)
}
