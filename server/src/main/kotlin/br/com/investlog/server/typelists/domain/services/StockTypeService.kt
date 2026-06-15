package br.com.investlog.server.typelists.domain.services

import br.com.investlog.server.shared.exceptions.NotFoundException
import br.com.investlog.server.shared.security.CurrentUserProvider
import br.com.investlog.server.typelists.domain.repositories.StockTypeRepository
import br.com.investlog.server.typelists.rest.dtos.TypeResponse
import java.util.UUID
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Service

@Service
class StockTypeService(
    private val currentUserProvider: CurrentUserProvider,
    private val stockTypeRepository: StockTypeRepository,
) {

    fun findAll(pageable: Pageable): PagedModel<TypeResponse> {
        val userId = currentUserProvider.getCurrentUser().id

        return stockTypeRepository.findAll(userId, pageable)
    }

    fun create(name: String): TypeResponse {
        val userId = currentUserProvider.getCurrentUser().id

        return stockTypeRepository.create(userId, name)
    }

    fun delete(externalId: UUID) {
        val userId = currentUserProvider.getCurrentUser().id

        if (stockTypeRepository.deleteByExternalId(userId, externalId) == 0) {
            throw NotFoundException("Stock type $externalId not found")
        }
    }
}
