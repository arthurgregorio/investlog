package br.com.investlog.server.wallets.rest.controllers

import br.com.investlog.server.wallets.domain.services.WalletService
import br.com.investlog.server.wallets.rest.payloads.WalletCreateRequest
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import br.com.investlog.server.wallets.rest.payloads.WalletUpdateRequest
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
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

@Validated
@RestController
@RequestMapping("/wallets")
class WalletController(private val walletService: WalletService) {

    @GetMapping
    fun findAll(pageable: Pageable): PagedModel<WalletResponse> = walletService.findAll(pageable)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: WalletCreateRequest): WalletResponse =
        walletService.create(request.name, request.kind, request.currency)

    @GetMapping("/{id}")
    fun findById(@PathVariable id: UUID): WalletResponse = walletService.findById(id)

    @PatchMapping("/{id}")
    fun update(@PathVariable id: UUID, @Valid @RequestBody request: WalletUpdateRequest): WalletResponse =
        walletService.update(id, request.name)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) = walletService.delete(id)
}
