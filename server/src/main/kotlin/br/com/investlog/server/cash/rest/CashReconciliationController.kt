package br.com.investlog.server.cash.rest

import br.com.investlog.server.cash.rest.payloads.CashReconciliationResponse
import br.com.investlog.server.cash.services.CashService
import br.com.investlog.server.shared.security.CurrentUserProvider
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class CashReconciliationController(
    private val currentUserProvider: CurrentUserProvider,
    private val cashService: CashService,
) {

    @GetMapping("/cash-reconciliation")
    fun findAll(): ResponseEntity<List<CashReconciliationResponse>> {

        val userId = currentUserProvider.getCurrentUser().id

        return ResponseEntity.ok(cashService.findReconciliation(userId))
    }

    @PostMapping("/wallets/{walletId}/cash-reconciliation")
    fun markReconciled(@PathVariable walletId: UUID): ResponseEntity<Void> {

        val userId = currentUserProvider.getCurrentUser().id
        cashService.markReconciled(userId, walletId)

        return ResponseEntity.noContent().build()
    }
}
