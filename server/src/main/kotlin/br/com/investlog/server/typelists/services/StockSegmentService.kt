package br.com.investlog.server.typelists.services

import br.com.investlog.server.shared.exceptions.NotFoundException
import br.com.investlog.server.typelists.repositories.StockSegmentRepository
import br.com.investlog.server.typelists.rest.payloads.TypeResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class StockSegmentService(
    private val stockSegmentRepository: StockSegmentRepository,
) {

    fun findAll(pageable: Pageable): PagedModel<TypeResponse> = stockSegmentRepository.findAll(pageable)

    @Transactional
    fun create(name: String): TypeResponse = stockSegmentRepository.create(name)

    @Transactional
    fun update(externalId: UUID, name: String): TypeResponse =
        stockSegmentRepository.update(externalId, name)
            ?: throw NotFoundException("Segmento $externalId não encontrado")

    @Transactional
    fun delete(externalId: UUID) {
        if (stockSegmentRepository.deleteByExternalId(externalId) == 0) {
            throw NotFoundException("Segmento $externalId não encontrado")
        }
    }
}
