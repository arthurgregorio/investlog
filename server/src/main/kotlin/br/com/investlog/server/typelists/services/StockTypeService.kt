package br.com.investlog.server.typelists.services

import br.com.investlog.server.shared.exceptions.NotFoundException
import br.com.investlog.server.typelists.repositories.StockTypeRepository
import br.com.investlog.server.typelists.rest.payloads.TypeResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class StockTypeService(
    private val stockTypeRepository: StockTypeRepository,
) {

    fun findAll(pageable: Pageable): PagedModel<TypeResponse> = stockTypeRepository.findAll(pageable)

    @Transactional
    fun create(name: String): TypeResponse = stockTypeRepository.create(name)

    @Transactional
    fun update(externalId: UUID, name: String): TypeResponse =
        stockTypeRepository.update(externalId, name)
            ?: throw NotFoundException("Tipo de ação $externalId não encontrado")

    @Transactional
    fun delete(externalId: UUID) {
        if (stockTypeRepository.deleteByExternalId(externalId) == 0) {
            throw NotFoundException("Tipo de ação $externalId não encontrado")
        }
    }
}
