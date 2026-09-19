package br.com.investlog.server.results.rest

import br.com.investlog.server.jooq.finances.enums.WalletKind
import br.com.investlog.server.results.rest.payloads.FundWithdrawalRequest
import br.com.investlog.server.results.rest.payloads.HoldingWithdrawalRequest
import br.com.investlog.server.results.services.WithdrawalService
import br.com.investlog.server.shared.security.CurrentUserProvider
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/wallets/{walletId}")
class WithdrawalController(
    private val currentUserProvider: CurrentUserProvider,
    private val withdrawalService: WithdrawalService,
) {

    @PostMapping("/stock-holdings/{holdingId}/withdrawals")
    fun withdrawFromStockHolding(
        @PathVariable walletId: UUID,
        @PathVariable holdingId: UUID,
        @Valid @RequestBody request: HoldingWithdrawalRequest,
    ): ResponseEntity<Void> {

        val userId = currentUserProvider.getCurrentUser().id
        withdrawalService.withdrawFromHolding(userId, walletId, holdingId, WalletKind.STOCKS, request)

        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    @PostMapping("/crypto-holdings/{holdingId}/withdrawals")
    fun withdrawFromCryptoHolding(
        @PathVariable walletId: UUID,
        @PathVariable holdingId: UUID,
        @Valid @RequestBody request: HoldingWithdrawalRequest,
    ): ResponseEntity<Void> {

        val userId = currentUserProvider.getCurrentUser().id
        withdrawalService.withdrawFromHolding(userId, walletId, holdingId, WalletKind.CRYPTO, request)

        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    @PostMapping("/fund-holdings/{holdingId}/withdrawals")
    fun withdrawFromFundHolding(
        @PathVariable walletId: UUID,
        @PathVariable holdingId: UUID,
        @Valid @RequestBody request: FundWithdrawalRequest,
    ): ResponseEntity<Void> {

        val userId = currentUserProvider.getCurrentUser().id
        withdrawalService.withdrawFromFund(userId, walletId, holdingId, request)

        return ResponseEntity.status(HttpStatus.CREATED).build()
    }
}
