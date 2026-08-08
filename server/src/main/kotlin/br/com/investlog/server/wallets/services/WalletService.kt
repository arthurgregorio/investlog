package br.com.investlog.server.wallets.services

import br.com.investlog.server.shared.exceptions.NotFoundException
import br.com.investlog.server.shared.security.CurrentUserProvider
import br.com.investlog.server.wallets.repositories.WalletRepository
import br.com.investlog.server.wallets.rest.payloads.WalletKind
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class WalletService(
    private val currentUserProvider: CurrentUserProvider,
    private val walletRepository: WalletRepository,
) {

    fun findAll(pageable: Pageable): PagedModel<WalletResponse> {
        val userId = currentUserProvider.getCurrentUser().id
        return walletRepository.findAll(userId, pageable)
    }

    @Transactional
    fun create(name: String, kind: WalletKind, currency: String): WalletResponse {
        val userId = currentUserProvider.getCurrentUser().id
        return walletRepository.create(userId, name, kind, currency)
    }

    fun findById(externalId: UUID): WalletResponse {
        val userId = currentUserProvider.getCurrentUser().id
        return walletRepository.findByExternalId(userId, externalId)
            ?: throw NotFoundException("Carteira $externalId não encontrada")
    }

    @Transactional
    fun update(externalId: UUID, name: String): WalletResponse {
        val userId = currentUserProvider.getCurrentUser().id
        return walletRepository.update(userId, externalId, name)
            ?: throw NotFoundException("Carteira $externalId não encontrada")
    }

    @Transactional
    fun delete(externalId: UUID) {
        val userId = currentUserProvider.getCurrentUser().id
        if (walletRepository.deleteByExternalId(userId, externalId) == 0) {
            throw NotFoundException("Carteira $externalId não encontrada")
        }
    }

    fun resolveId(externalId: UUID): Long {
        val userId = currentUserProvider.getCurrentUser().id
        return walletRepository.findInternalId(userId, externalId)
            ?: throw NotFoundException("Carteira $externalId não encontrada")
    }
}
