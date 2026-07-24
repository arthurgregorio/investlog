package br.com.investlog.server.typelists.domain.services

import br.com.investlog.server.shared.exceptions.NotFoundException
import br.com.investlog.server.typelists.domain.repositories.StockTypeRepository
import br.com.investlog.server.typelists.rest.payloads.TypeResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class StockTypeService(
    private val stockTypeRepository: StockTypeRepository,
) {

    fun findAll(pageable: Pageable): PagedModel<TypeResponse> = stockTypeRepository.findAll(pageable)

    fun create(name: String): TypeResponse = stockTypeRepository.create(name)

    fun delete(externalId: UUID) {
        if (stockTypeRepository.deleteByExternalId(externalId) == 0) {
            throw NotFoundException("Stock type $externalId not found")
        }
    }
}
