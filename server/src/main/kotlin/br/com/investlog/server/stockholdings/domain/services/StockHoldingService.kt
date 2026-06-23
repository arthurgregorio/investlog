package br.com.investlog.server.stockholdings.domain.services

import br.com.investlog.server.shared.exceptions.NotFoundException
import br.com.investlog.server.stockholdings.domain.repositories.StockHoldingRepository
import br.com.investlog.server.stockholdings.domain.repositories.StockLotRepository
import br.com.investlog.server.stockholdings.rest.payloads.LotCreateRequest
import br.com.investlog.server.stockholdings.rest.payloads.LotResponse
import br.com.investlog.server.stockholdings.rest.payloads.LotUpdateRequest
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingCreateRequest
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingResponse
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingUpdateRequest
import br.com.investlog.server.wallets.domain.services.WalletService
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class StockHoldingService(
    private val walletService: WalletService,
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
            ?: throw NotFoundException("Stock holding not found: $holdingExternalId")
    }

    @Transactional
    fun create(walletExternalId: UUID, request: StockHoldingCreateRequest): StockHoldingResponse {
        val walletId = walletService.resolveId(walletExternalId)
        val stockTypeId = holdingRepo.findStockTypeInternalId(request.stockTypeId)
            ?: throw NotFoundException("Stock type not found: ${request.stockTypeId}")
        return holdingRepo.create(
            walletInternalId = walletId,
            stockTypeInternalId = stockTypeId,
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
        val stockTypeId = request.stockTypeId?.let {
            holdingRepo.findStockTypeInternalId(it) ?: throw NotFoundException("Stock type not found: $it")
        }
        return holdingRepo.update(
            walletInternalId = walletId,
            externalId = holdingExternalId,
            stockTypeInternalId = stockTypeId,
            ticker = request.ticker,
            name = request.name,
            currentPrice = request.currentPrice,
        ) ?: throw NotFoundException("Stock holding not found: $holdingExternalId")
    }

    @Transactional
    fun delete(walletExternalId: UUID, holdingExternalId: UUID) {
        val walletId = walletService.resolveId(walletExternalId)
        val deleted = holdingRepo.deleteByExternalId(walletId, holdingExternalId)
        if (deleted == 0) throw NotFoundException("Stock holding not found: $holdingExternalId")
    }

    @Transactional
    fun addLot(walletExternalId: UUID, holdingExternalId: UUID, request: LotCreateRequest): LotResponse {
        val walletId = walletService.resolveId(walletExternalId)
        val holdingId = holdingRepo.findInternalId(walletId, holdingExternalId)
            ?: throw NotFoundException("Stock holding not found: $holdingExternalId")
        return lotRepo.addLot(holdingId, request)
    }

    @Transactional
    fun deleteLot(walletExternalId: UUID, holdingExternalId: UUID, lotExternalId: UUID) {
        val walletId = walletService.resolveId(walletExternalId)
        val holdingId = holdingRepo.findInternalId(walletId, holdingExternalId)
            ?: throw NotFoundException("Stock holding not found: $holdingExternalId")
        val deleted = lotRepo.deleteByExternalId(holdingId, lotExternalId)
        if (deleted == 0) throw NotFoundException("Lot not found: $lotExternalId")
    }

    @Transactional
    fun updateLotDate(
        walletExternalId: UUID,
        holdingExternalId: UUID,
        lotExternalId: UUID,
        request: LotUpdateRequest,
    ): LotResponse {
        val walletId = walletService.resolveId(walletExternalId)
        val holdingId = holdingRepo.findInternalId(walletId, holdingExternalId)
            ?: throw NotFoundException("Stock holding not found: $holdingExternalId")
        return lotRepo.updateLotDate(holdingId, lotExternalId, request.lotDate)
            ?: throw NotFoundException("Lot not found: $lotExternalId")
    }
}
