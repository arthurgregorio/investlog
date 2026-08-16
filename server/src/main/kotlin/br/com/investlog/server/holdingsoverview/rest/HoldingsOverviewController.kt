package br.com.investlog.server.holdingsoverview.rest

import br.com.investlog.server.holdingsoverview.repositories.HoldingsOverviewRepository
import br.com.investlog.server.holdingsoverview.rest.payloads.HoldingRowResponse
import br.com.investlog.server.shared.security.CurrentUserProvider
import br.com.investlog.server.wallets.rest.payloads.WalletKind
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/holdings")
class HoldingsOverviewController(
    private val currentUserProvider: CurrentUserProvider,
    private val holdingsOverviewRepository: HoldingsOverviewRepository
) {

    @GetMapping
    fun findAll(
        @RequestParam(required = false) kind: WalletKind?,
        @RequestParam(required = false) typeLabel: String?,
        @RequestParam(required = false) walletId: UUID?,
        @RequestParam(required = false) search: String?,
        pageable: Pageable,
    ): ResponseEntity<PagedModel<HoldingRowResponse>> {

        val userId = currentUserProvider.getCurrentUser().id

        val holdings = holdingsOverviewRepository.findAll(
            userId, kind?.jooqWalletKind, typeLabel, walletId, search, pageable
        )

        return ResponseEntity.ok(holdings)
    }

    @GetMapping("/report")
    fun findAllForReport(
        @RequestParam(required = false) kind: WalletKind?,
        @RequestParam(required = false) typeLabel: String?,
        @RequestParam(required = false) walletId: UUID?,
        @RequestParam(required = false) search: String?,
    ): ResponseEntity<List<HoldingRowResponse>> {

        val userId = currentUserProvider.getCurrentUser().id

        val holdings = holdingsOverviewRepository.findAllForReport(
            userId, kind?.jooqWalletKind, typeLabel, walletId, search
        )

        return ResponseEntity.ok(holdings)
    }
}
