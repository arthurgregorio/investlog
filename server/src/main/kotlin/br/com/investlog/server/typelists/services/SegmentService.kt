package br.com.investlog.server.typelists.services

import br.com.investlog.server.shared.exceptions.NotFoundException
import br.com.investlog.server.typelists.repositories.SegmentRepository
import br.com.investlog.server.typelists.rest.payloads.TypeResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class SegmentService(
    private val segmentRepository: SegmentRepository,
) {

    fun findAll(pageable: Pageable): PagedModel<TypeResponse> = segmentRepository.findAll(pageable)

    fun resolveId(externalId: UUID): Long =
        segmentRepository.findInternalId(externalId)
            ?: throw NotFoundException("Segmento não encontrado: $externalId")

    @Transactional
    fun create(name: String): TypeResponse = segmentRepository.create(name)

    @Transactional
    fun update(externalId: UUID, name: String): TypeResponse =
        segmentRepository.update(externalId, name)
            ?: throw NotFoundException("Segmento $externalId não encontrado")

    @Transactional
    fun delete(externalId: UUID) {
        if (segmentRepository.deleteByExternalId(externalId) == 0) {
            throw NotFoundException("Segmento $externalId não encontrado")
        }
    }
}
