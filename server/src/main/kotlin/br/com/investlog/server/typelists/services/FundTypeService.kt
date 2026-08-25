package br.com.investlog.server.typelists.services

import br.com.investlog.server.shared.exceptions.NotFoundException
import br.com.investlog.server.typelists.repositories.FundTypeRepository
import br.com.investlog.server.typelists.rest.payloads.TypeResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class FundTypeService(
    private val fundTypeRepository: FundTypeRepository,
) {

    fun findAll(pageable: Pageable): PagedModel<TypeResponse> = fundTypeRepository.findAll(pageable)

    @Transactional
    fun create(name: String): TypeResponse = fundTypeRepository.create(name)

    @Transactional
    fun update(externalId: UUID, name: String): TypeResponse =
        fundTypeRepository.update(externalId, name)
            ?: throw NotFoundException("Tipo de fundo $externalId não encontrado")

    @Transactional
    fun delete(externalId: UUID) {
        if (fundTypeRepository.deleteByExternalId(externalId) == 0) {
            throw NotFoundException("Tipo de fundo $externalId não encontrado")
        }
    }
}
