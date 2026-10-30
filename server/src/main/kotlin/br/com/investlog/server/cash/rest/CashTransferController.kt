package br.com.investlog.server.cash.rest

import br.com.investlog.server.cash.rest.payloads.CashTransferRequest
import br.com.investlog.server.cash.rest.payloads.CashTransferResponse
import br.com.investlog.server.cash.services.CashService
import br.com.investlog.server.shared.security.CurrentUserProvider
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/wallets/{walletId}/cash-transfers")
class CashTransferController(
    private val currentUserProvider: CurrentUserProvider,
    private val cashService: CashService,
) {

    @PostMapping
    fun transfer(
        @PathVariable walletId: UUID,
        @Valid @RequestBody request: CashTransferRequest,
    ): ResponseEntity<CashTransferResponse> {

        val userId = currentUserProvider.getCurrentUser().id

        return ResponseEntity.status(HttpStatus.CREATED).body(cashService.transfer(userId, walletId, request))
    }

    @DeleteMapping("/{transferId}")
    fun delete(@PathVariable walletId: UUID, @PathVariable transferId: UUID): ResponseEntity<Void> {

        val userId = currentUserProvider.getCurrentUser().id
        cashService.deleteTransfer(userId, walletId, transferId)

        return ResponseEntity.noContent().build()
    }
}
