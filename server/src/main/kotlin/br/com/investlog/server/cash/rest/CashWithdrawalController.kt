package br.com.investlog.server.cash.rest

import br.com.investlog.server.cash.rest.payloads.CashWithdrawalRequest
import br.com.investlog.server.cash.rest.payloads.CashWithdrawalResponse
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
@RequestMapping("/wallets/{walletId}/cash-withdrawals")
class CashWithdrawalController(
    private val currentUserProvider: CurrentUserProvider,
    private val cashService: CashService,
) {

    @PostMapping
    fun withdraw(
        @PathVariable walletId: UUID,
        @Valid @RequestBody request: CashWithdrawalRequest,
    ): ResponseEntity<CashWithdrawalResponse> {

        val userId = currentUserProvider.getCurrentUser().id

        return ResponseEntity.status(HttpStatus.CREATED).body(cashService.withdrawToBank(userId, walletId, request))
    }

    @DeleteMapping("/{withdrawalId}")
    fun delete(@PathVariable walletId: UUID, @PathVariable withdrawalId: UUID): ResponseEntity<Void> {

        val userId = currentUserProvider.getCurrentUser().id
        cashService.deleteBankWithdrawal(userId, walletId, withdrawalId)

        return ResponseEntity.noContent().build()
    }
}
