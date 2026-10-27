package br.com.investlog.server.typelists.rest

import br.com.investlog.server.typelists.rest.payloads.HoldingTypeCreateRequest
import br.com.investlog.server.typelists.rest.payloads.HoldingTypeResponse
import br.com.investlog.server.typelists.rest.payloads.HoldingTypeUpdateRequest
import br.com.investlog.server.typelists.services.HoldingTypeService
import br.com.investlog.server.wallets.rest.payloads.WalletKind
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/holding-types")
class HoldingTypeController(
    private val holdingTypeService: HoldingTypeService
) {

    @GetMapping
    fun findAll(
        @RequestParam(required = false) kind: WalletKind?,
        pageable: Pageable,
    ): ResponseEntity<PagedModel<HoldingTypeResponse>> =
        ResponseEntity.ok(holdingTypeService.findAll(kind, pageable))

    @PostMapping
    fun create(@Valid @RequestBody request: HoldingTypeCreateRequest): ResponseEntity<HoldingTypeResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(holdingTypeService.create(request))

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: HoldingTypeUpdateRequest,
    ): ResponseEntity<HoldingTypeResponse> =
        ResponseEntity.ok(holdingTypeService.update(id, request))

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        holdingTypeService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
