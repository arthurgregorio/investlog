package br.com.investlog.server.walletledger.rest

import br.com.investlog.server.shared.exceptions.NotFoundException
import br.com.investlog.server.shared.security.CurrentUserProvider
import br.com.investlog.server.walletledger.repositories.WalletLedgerRepository
import br.com.investlog.server.walletledger.rest.payloads.WalletLedgerEntryResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/wallets/{walletId}/ledger")
class WalletLedgerController(
    private val currentUserProvider: CurrentUserProvider,
    private val walletLedgerRepository: WalletLedgerRepository,
) {

    @GetMapping
    fun findAll(
        @PathVariable walletId: UUID,
        pageable: Pageable,
    ): ResponseEntity<PagedModel<WalletLedgerEntryResponse>> {

        val userId = currentUserProvider.getCurrentUser().id
        val internalWalletId = walletLedgerRepository.findWalletId(userId, walletId)
            ?: throw NotFoundException("Carteira não encontrada")

        return ResponseEntity.ok(walletLedgerRepository.findByWallet(internalWalletId, pageable))
    }
}
