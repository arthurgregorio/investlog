package br.com.investlog.server.holdingsoverview.services

import br.com.investlog.server.holdingsoverview.repositories.HoldingsOverviewRepository
import br.com.investlog.server.holdingsoverview.rest.payloads.HoldingRowResponse
import br.com.investlog.server.shared.security.CurrentUserProvider
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import br.com.investlog.server.jooq.finances.enums.WalletKind as JooqWalletKind

@Service
@Transactional(readOnly = true)
class HoldingsOverviewService(
    private val currentUserProvider: CurrentUserProvider,
    private val holdingsOverviewRepository: HoldingsOverviewRepository,
) {

    fun findAll(
        kind: String?,
        typeLabel: String?,
        walletId: UUID?,
        search: String?,
        pageable: Pageable,
    ): PagedModel<HoldingRowResponse> {
        val userId = currentUserProvider.getCurrentUser().id
        val jooqKind = kind?.let {
            try {
                JooqWalletKind.valueOf(it)
            } catch (ex: IllegalArgumentException) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de carteira inválido: $it", ex)
            }
        }
        return holdingsOverviewRepository.findAll(userId, jooqKind, typeLabel, walletId, search, pageable)
    }
}
