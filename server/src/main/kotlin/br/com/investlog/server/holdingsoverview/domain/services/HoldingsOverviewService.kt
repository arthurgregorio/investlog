package br.com.investlog.server.holdingsoverview.domain.services

import br.com.investlog.server.holdingsoverview.domain.repositories.HoldingsOverviewRepository
import br.com.investlog.server.holdingsoverview.rest.payloads.HoldingRowResponse
import br.com.investlog.server.shared.security.CurrentUserProvider
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import br.com.investlog.server.jooq.finances.enums.WalletKind as JooqWalletKind

@Service
class HoldingsOverviewService(
    private val currentUserProvider: CurrentUserProvider,
    private val holdingsOverviewRepository: HoldingsOverviewRepository,
) {

    fun findAll(kind: String?, pageable: Pageable): PagedModel<HoldingRowResponse> {
        val userId = currentUserProvider.getCurrentUser().id
        val jooqKind = kind?.let {
            try {
                JooqWalletKind.valueOf(it)
            } catch (ex: IllegalArgumentException) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid kind: $it", ex)
            }
        }
        return holdingsOverviewRepository.findAll(userId, jooqKind, pageable)
    }
}
