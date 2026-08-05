package br.com.investlog.server.wallets.rest

import br.com.investlog.server.wallets.services.WalletService
import br.com.investlog.server.wallets.rest.payloads.WalletCreateRequest
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import br.com.investlog.server.wallets.rest.payloads.WalletUpdateRequest
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Validated
@RestController
@RequestMapping("/wallets")
class WalletController(
    private val walletService: WalletService
) {

    @GetMapping
    fun findAll(pageable: Pageable): ResponseEntity<PagedModel<WalletResponse>> =
        ResponseEntity.ok(walletService.findAll(pageable))

    @PostMapping
    fun create(@Valid @RequestBody request: WalletCreateRequest): ResponseEntity<WalletResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(walletService.create(request.name, request.kind, request.currency))

    @GetMapping("/{id}")
    fun findById(@PathVariable id: UUID): ResponseEntity<WalletResponse> =
        ResponseEntity.ok(walletService.findById(id))

    @PatchMapping("/{id}")
    fun update(@PathVariable id: UUID, @Valid @RequestBody request: WalletUpdateRequest): ResponseEntity<WalletResponse> =
        ResponseEntity.ok(walletService.update(id, request.name))

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        walletService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
