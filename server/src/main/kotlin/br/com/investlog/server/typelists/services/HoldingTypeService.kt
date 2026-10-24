package br.com.investlog.server.typelists.services

import br.com.investlog.server.shared.exceptions.HoldingTypeKindInUseException
import br.com.investlog.server.shared.exceptions.HoldingTypeKindMismatchException
import br.com.investlog.server.shared.exceptions.NotFoundException
import br.com.investlog.server.typelists.repositories.HoldingTypeRepository
import br.com.investlog.server.typelists.rest.payloads.HoldingTypeCreateRequest
import br.com.investlog.server.typelists.rest.payloads.HoldingTypeResponse
import br.com.investlog.server.typelists.rest.payloads.HoldingTypeUpdateRequest
import br.com.investlog.server.wallets.rest.payloads.WalletKind
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import br.com.investlog.server.jooq.finances.enums.WalletKind as JooqWalletKind

@Service
@Transactional(readOnly = true)
class HoldingTypeService(
    private val holdingTypeRepository: HoldingTypeRepository,
) {

    fun findAll(kind: WalletKind?, pageable: Pageable): PagedModel<HoldingTypeResponse> =
        holdingTypeRepository.findAll(kind?.jooqWalletKind, pageable)

    fun resolveId(externalId: UUID, kind: JooqWalletKind): Long {
        val holdingType = holdingTypeRepository.findReference(externalId)
            ?: throw NotFoundException("Tipo não encontrado: $externalId")
        if (kind !in holdingType.kinds) {
            throw HoldingTypeKindMismatchException("Tipo não se aplica a este tipo de carteira")
        }
        return holdingType.id
    }

    @Transactional
    fun create(request: HoldingTypeCreateRequest): HoldingTypeResponse =
        holdingTypeRepository.create(request.name, request.kinds)

    @Transactional
    fun update(externalId: UUID, request: HoldingTypeUpdateRequest): HoldingTypeResponse {
        val holdingType = holdingTypeRepository.findReference(externalId)
            ?: throw NotFoundException("Tipo $externalId não encontrado")

        val keptKinds = request.kinds.map { kind -> kind.jooqWalletKind }.toSet()
        val removedKindInUse = (holdingType.kinds - keptKinds)
            .any { kind -> holdingTypeRepository.countUsage(holdingType.id, kind) > 0 }
        if (removedKindInUse) {
            throw HoldingTypeKindInUseException("Este tipo ainda está em uso por investimentos de um tipo de carteira removido")
        }

        return holdingTypeRepository.update(holdingType.id, request.name, request.kinds)
    }

    @Transactional
    fun delete(externalId: UUID) {
        if (holdingTypeRepository.deleteByExternalId(externalId) == 0) {
            throw NotFoundException("Tipo $externalId não encontrado")
        }
    }
}
