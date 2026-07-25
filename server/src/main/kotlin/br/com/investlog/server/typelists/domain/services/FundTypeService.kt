package br.com.investlog.server.typelists.domain.services

import br.com.investlog.server.shared.exceptions.NotFoundException
import br.com.investlog.server.typelists.domain.repositories.FundTypeRepository
import br.com.investlog.server.typelists.rest.payloads.TypeResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class FundTypeService(
    private val fundTypeRepository: FundTypeRepository,
) {

    fun findAll(pageable: Pageable): PagedModel<TypeResponse> = fundTypeRepository.findAll(pageable)

    fun create(name: String): TypeResponse = fundTypeRepository.create(name)

    fun delete(externalId: UUID) {
        if (fundTypeRepository.deleteByExternalId(externalId) == 0) {
            throw NotFoundException("Fund type $externalId not found")
        }
    }
}
