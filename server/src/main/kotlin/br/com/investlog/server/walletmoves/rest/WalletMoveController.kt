package br.com.investlog.server.walletmoves.rest

import br.com.investlog.server.shared.exceptions.NotFoundException
import br.com.investlog.server.shared.security.CurrentUserProvider
import br.com.investlog.server.walletmoves.repositories.WalletMoveRepository
import br.com.investlog.server.walletmoves.rest.payloads.WalletMoveRequest
import br.com.investlog.server.walletmoves.rest.payloads.WalletMoveResponse
import br.com.investlog.server.walletmoves.services.WalletMoveService
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/wallets/{walletId}/moves")
class WalletMoveController(
    private val currentUserProvider: CurrentUserProvider,
    private val walletMoveService: WalletMoveService,
    private val walletMoveRepository: WalletMoveRepository,
) {

    @PostMapping
    fun move(
        @PathVariable walletId: UUID,
        @Valid @RequestBody request: WalletMoveRequest,
    ): ResponseEntity<Void> {

        val userId = currentUserProvider.getCurrentUser().id
        walletMoveService.move(userId, walletId, request)

        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    @GetMapping
    fun findAll(
        @PathVariable walletId: UUID,
        pageable: Pageable,
    ): ResponseEntity<PagedModel<WalletMoveResponse>> {

        val userId = currentUserProvider.getCurrentUser().id
        val wallet = walletMoveRepository.findWallet(userId, walletId)
            ?: throw NotFoundException("Carteira não encontrada")

        return ResponseEntity.ok(walletMoveRepository.findByWallet(wallet, pageable))
    }
}
