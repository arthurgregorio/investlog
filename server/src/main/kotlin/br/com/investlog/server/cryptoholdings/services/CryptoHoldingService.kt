package br.com.investlog.server.cryptoholdings.services

import br.com.investlog.server.cryptoholdings.repositories.CryptoHoldingRepository
import br.com.investlog.server.cryptoholdings.repositories.CryptoLotRepository
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingCreateRequest
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingResponse
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingUpdateRequest
import br.com.investlog.server.shared.exceptions.NotFoundException
import br.com.investlog.server.stockholdings.rest.payloads.LotCreateRequest
import br.com.investlog.server.stockholdings.rest.payloads.LotResponse
import br.com.investlog.server.stockholdings.rest.payloads.LotUpdateRequest
import br.com.investlog.server.wallets.services.WalletService
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class CryptoHoldingService(
    private val walletService: WalletService,
    private val holdingRepo: CryptoHoldingRepository,
    private val lotRepo: CryptoLotRepository,
) {

    fun findAll(walletExternalId: UUID, pageable: Pageable): PagedModel<CryptoHoldingResponse> {
        val walletId = walletService.resolveId(walletExternalId)
        return holdingRepo.findAll(walletId, pageable)
    }

    fun findById(walletExternalId: UUID, holdingExternalId: UUID): CryptoHoldingResponse {
        val walletId = walletService.resolveId(walletExternalId)
        return holdingRepo.findByExternalId(walletId, holdingExternalId)
            ?: throw NotFoundException("Posição de criptomoeda não encontrada: $holdingExternalId")
    }

    @Transactional
    fun create(walletExternalId: UUID, request: CryptoHoldingCreateRequest): CryptoHoldingResponse {
        val walletId = walletService.resolveId(walletExternalId)
        return holdingRepo.create(
            walletInternalId = walletId,
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
        request: CryptoHoldingUpdateRequest
    ): CryptoHoldingResponse {
        val walletId = walletService.resolveId(walletExternalId)
        return holdingRepo.update(
            walletInternalId = walletId,
            externalId = holdingExternalId,
            ticker = request.ticker,
            name = request.name,
            currentPrice = request.currentPrice,
        ) ?: throw NotFoundException("Posição de criptomoeda não encontrada: $holdingExternalId")
    }

    @Transactional
    fun delete(walletExternalId: UUID, holdingExternalId: UUID) {
        val walletId = walletService.resolveId(walletExternalId)
        if (holdingRepo.deleteByExternalId(walletId, holdingExternalId) == 0) {
            throw NotFoundException("Posição de criptomoeda não encontrada: $holdingExternalId")
        }
    }

    @Transactional
    fun addLot(walletExternalId: UUID, holdingExternalId: UUID, request: LotCreateRequest): LotResponse {
        val walletId = walletService.resolveId(walletExternalId)
        val holdingId = holdingRepo.findInternalId(walletId, holdingExternalId)
            ?: throw NotFoundException("Posição de criptomoeda não encontrada: $holdingExternalId")
        return lotRepo.addLot(holdingId, request)
    }

    @Transactional
    fun deleteLot(walletExternalId: UUID, holdingExternalId: UUID, lotExternalId: UUID) {
        val walletId = walletService.resolveId(walletExternalId)
        val holdingId = holdingRepo.findInternalId(walletId, holdingExternalId)
            ?: throw NotFoundException("Posição de criptomoeda não encontrada: $holdingExternalId")
        if (lotRepo.deleteByExternalId(holdingId, lotExternalId) == 0) {
            throw NotFoundException("Lote não encontrado: $lotExternalId")
        }
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
            ?: throw NotFoundException("Posição de criptomoeda não encontrada: $holdingExternalId")
        return lotRepo.updateLotDate(holdingId, lotExternalId, request.lotDate)
            ?: throw NotFoundException("Lote não encontrado: $lotExternalId")
    }
}
