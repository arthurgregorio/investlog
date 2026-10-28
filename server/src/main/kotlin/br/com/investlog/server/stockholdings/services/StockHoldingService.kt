package br.com.investlog.server.stockholdings.services

import br.com.investlog.server.jooq.finances.enums.WalletKind
import br.com.investlog.server.shared.exceptions.FrozenHoldingException
import br.com.investlog.server.shared.exceptions.InvalidLotUpdateException
import br.com.investlog.server.shared.exceptions.NotFoundException
import br.com.investlog.server.stockholdings.repositories.StockHoldingRepository
import br.com.investlog.server.stockholdings.repositories.StockLotRepository
import br.com.investlog.server.stockholdings.rest.payloads.LotCreateRequest
import br.com.investlog.server.stockholdings.rest.payloads.LotResponse
import br.com.investlog.server.stockholdings.rest.payloads.LotUpdateRequest
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingCreateRequest
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingResponse
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingUpdateRequest
import br.com.investlog.server.strategies.rest.payloads.HoldingClassRequest
import br.com.investlog.server.strategies.services.StrategyClassService
import br.com.investlog.server.typelists.rest.payloads.HoldingSegmentRequest
import br.com.investlog.server.typelists.services.HoldingTypeService
import br.com.investlog.server.typelists.services.SegmentService
import br.com.investlog.server.wallets.services.WalletService
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class StockHoldingService(
    private val walletService: WalletService,
    private val holdingTypeService: HoldingTypeService,
    private val segmentService: SegmentService,
    private val strategyClassService: StrategyClassService,
    private val holdingRepo: StockHoldingRepository,
    private val lotRepo: StockLotRepository,
) {

    fun findAll(walletExternalId: UUID, pageable: Pageable): PagedModel<StockHoldingResponse> {
        val walletId = walletService.resolveId(walletExternalId)
        return holdingRepo.findAll(walletId, pageable)
    }

    fun findById(walletExternalId: UUID, holdingExternalId: UUID): StockHoldingResponse {
        val walletId = walletService.resolveId(walletExternalId)
        return holdingRepo.findByExternalId(walletId, holdingExternalId)
            ?: throw NotFoundException("Posição de ação não encontrada: $holdingExternalId")
    }

    @Transactional
    fun create(walletExternalId: UUID, request: StockHoldingCreateRequest): StockHoldingResponse {
        val walletId = walletService.resolveId(walletExternalId)
        return holdingRepo.create(
            walletInternalId = walletId,
            typeInternalId = holdingTypeService.resolveId(request.typeId, WalletKind.STOCKS),
            segmentInternalId = request.segmentId?.let(segmentService::resolveId),
            strategyClassInternalId = strategyClassService.resolveId(request.strategyClassId),
            ticker = request.ticker,
            name = request.name ?: request.ticker.uppercase(),
            currentPrice = request.currentPrice,
            lot = request.lot,
        )
    }

    @Transactional
    fun update(
        walletExternalId: UUID,
        holdingExternalId: UUID,
        request: StockHoldingUpdateRequest
    ): StockHoldingResponse {
        val walletId = walletService.resolveId(walletExternalId)
        return holdingRepo.update(
            walletInternalId = walletId,
            externalId = holdingExternalId,
            typeInternalId = request.typeId?.let { typeId -> holdingTypeService.resolveId(typeId, WalletKind.STOCKS) },
            ticker = request.ticker,
            name = request.name,
            currentPrice = request.currentPrice,
            frozen = request.frozen,
        ) ?: throw NotFoundException("Posição de ação não encontrada: $holdingExternalId")
    }

    @Transactional
    fun updateSegment(
        walletExternalId: UUID,
        holdingExternalId: UUID,
        request: HoldingSegmentRequest,
    ): StockHoldingResponse {
        val walletId = walletService.resolveId(walletExternalId)
        return holdingRepo.updateSegment(
            walletInternalId = walletId,
            externalId = holdingExternalId,
            segmentInternalId = request.segmentId?.let(segmentService::resolveId),
        ) ?: throw NotFoundException("Posição de ação não encontrada: $holdingExternalId")
    }

    @Transactional
    fun updateClass(
        walletExternalId: UUID,
        holdingExternalId: UUID,
        request: HoldingClassRequest,
    ): StockHoldingResponse {
        val walletId = walletService.resolveId(walletExternalId)
        val holdingId = holdingRepo.findInternalId(walletId, holdingExternalId)
            ?: throw NotFoundException("Posição de ação não encontrada: $holdingExternalId")
        strategyClassService.assignHolding(WalletKind.STOCKS, holdingId, request.strategyClassId)
        return findById(walletExternalId, holdingExternalId)
    }

    @Transactional
    fun delete(walletExternalId: UUID, holdingExternalId: UUID) {
        val walletId = walletService.resolveId(walletExternalId)
        val deleted = holdingRepo.deleteByExternalId(walletId, holdingExternalId)
        if (deleted == 0) throw NotFoundException("Posição de ação não encontrada: $holdingExternalId")
    }

    @Transactional
    fun addLot(walletExternalId: UUID, holdingExternalId: UUID, request: LotCreateRequest): LotResponse {
        val walletId = walletService.resolveId(walletExternalId)
        val holdingId = holdingRepo.findInternalId(walletId, holdingExternalId)
            ?: throw NotFoundException("Posição de ação não encontrada: $holdingExternalId")
        if (holdingRepo.isFrozen(holdingId)) {
            throw FrozenHoldingException("Esta posição está congelada e não aceita novos lotes")
        }
        return lotRepo.addLot(holdingId, request)
    }

    @Transactional
    fun deleteLot(walletExternalId: UUID, holdingExternalId: UUID, lotExternalId: UUID) {
        val walletId = walletService.resolveId(walletExternalId)
        val holdingId = holdingRepo.findInternalId(walletId, holdingExternalId)
            ?: throw NotFoundException("Posição de ação não encontrada: $holdingExternalId")
        val deleted = lotRepo.deleteByExternalId(holdingId, lotExternalId)
        if (deleted == 0) throw NotFoundException("Lote não encontrado: $lotExternalId")
    }

    @Transactional
    fun updateLot(
        walletExternalId: UUID,
        holdingExternalId: UUID,
        lotExternalId: UUID,
        request: LotUpdateRequest,
    ): LotResponse {
        val walletId = walletService.resolveId(walletExternalId)
        val holdingId = holdingRepo.findInternalId(walletId, holdingExternalId)
            ?: throw NotFoundException("Posição de ação não encontrada: $holdingExternalId")
        val lot = lotRepo.updateLot(holdingId, lotExternalId, request)
            ?: throw NotFoundException("Lote não encontrado: $lotExternalId")
        if (request.quantity != null && lotRepo.sumQuantity(holdingId) < lotRepo.sumExitedQuantity(holdingId)) {
            throw InvalidLotUpdateException()
        }
        return lot
    }
}
