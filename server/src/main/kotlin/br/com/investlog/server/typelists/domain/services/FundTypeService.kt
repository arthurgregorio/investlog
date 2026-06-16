package br.com.investlog.server.typelists.domain.services

import br.com.investlog.server.shared.exceptions.NotFoundException
import br.com.investlog.server.shared.security.CurrentUserProvider
import br.com.investlog.server.typelists.domain.repositories.FundTypeRepository
import br.com.investlog.server.typelists.rest.payloads.TypeResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class FundTypeService(
    private val currentUserProvider: CurrentUserProvider,
    private val fundTypeRepository: FundTypeRepository,
) {

    fun findAll(pageable: Pageable): PagedModel<TypeResponse> {
        val userId = currentUserProvider.getCurrentUser().id

        return fundTypeRepository.findAll(userId, pageable)
    }

    fun create(name: String): TypeResponse {
        val userId = currentUserProvider.getCurrentUser().id

        return fundTypeRepository.create(userId, name)
    }

    fun delete(externalId: UUID) {
        val userId = currentUserProvider.getCurrentUser().id

        if (fundTypeRepository.deleteByExternalId(userId, externalId) == 0) {
            throw NotFoundException("Fund type $externalId not found")
        }
    }
}
