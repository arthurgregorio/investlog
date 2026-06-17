package br.com.investlog.server.fundholdings.domain.services

import br.com.investlog.server.fundholdings.domain.repositories.FundContributionRepository
import br.com.investlog.server.fundholdings.domain.repositories.FundHoldingRepository
import br.com.investlog.server.fundholdings.rest.payloads.ContributionCreateRequest
import br.com.investlog.server.fundholdings.rest.payloads.ContributionResponse
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingCreateRequest
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingResponse
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingUpdateRequest
import br.com.investlog.server.shared.exceptions.NotFoundException
import br.com.investlog.server.wallets.domain.services.WalletService
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class FundHoldingService(
    private val walletService: WalletService,
    private val holdingRepo: FundHoldingRepository,
    private val contributionRepo: FundContributionRepository,
) {

    fun findAll(walletExternalId: UUID, pageable: Pageable): PagedModel<FundHoldingResponse> {
        val walletId = walletService.resolveId(walletExternalId)
        return holdingRepo.findAll(walletId, pageable)
    }

    fun findById(walletExternalId: UUID, holdingExternalId: UUID): FundHoldingResponse {
        val walletId = walletService.resolveId(walletExternalId)
        return holdingRepo.findByExternalId(walletId, holdingExternalId)
            ?: throw NotFoundException("Fund holding not found: $holdingExternalId")
    }

    @Transactional
    fun create(walletExternalId: UUID, request: FundHoldingCreateRequest): FundHoldingResponse {
        val walletId = walletService.resolveId(walletExternalId)
        val fundTypeId = holdingRepo.findFundTypeInternalId(request.fundTypeId)
            ?: throw NotFoundException("Fund type not found: ${request.fundTypeId}")
        return holdingRepo.create(
            walletInternalId = walletId,
            fundTypeInternalId = fundTypeId,
            name = request.name,
            currentValue = request.currentValue,
            contribution = request.contribution,
        )
    }

    @Transactional
    fun update(walletExternalId: UUID, holdingExternalId: UUID, request: FundHoldingUpdateRequest): FundHoldingResponse {
        val walletId = walletService.resolveId(walletExternalId)
        val fundTypeId = request.fundTypeId?.let {
            holdingRepo.findFundTypeInternalId(it) ?: throw NotFoundException("Fund type not found: $it")
        }
        return holdingRepo.update(
            walletInternalId = walletId,
            externalId = holdingExternalId,
            fundTypeInternalId = fundTypeId,
            name = request.name,
            currentValue = request.currentValue,
        ) ?: throw NotFoundException("Fund holding not found: $holdingExternalId")
    }

    @Transactional
    fun delete(walletExternalId: UUID, holdingExternalId: UUID) {
        val walletId = walletService.resolveId(walletExternalId)
        if (holdingRepo.deleteByExternalId(walletId, holdingExternalId) == 0) {
            throw NotFoundException("Fund holding not found: $holdingExternalId")
        }
    }

    @Transactional
    fun addContribution(walletExternalId: UUID, holdingExternalId: UUID, request: ContributionCreateRequest): ContributionResponse {
        val walletId = walletService.resolveId(walletExternalId)
        val holdingId = holdingRepo.findInternalId(walletId, holdingExternalId)
            ?: throw NotFoundException("Fund holding not found: $holdingExternalId")
        return contributionRepo.addContribution(holdingId, request)
    }

    @Transactional
    fun deleteContribution(walletExternalId: UUID, holdingExternalId: UUID, contributionExternalId: UUID) {
        val walletId = walletService.resolveId(walletExternalId)
        val holdingId = holdingRepo.findInternalId(walletId, holdingExternalId)
            ?: throw NotFoundException("Fund holding not found: $holdingExternalId")
        if (contributionRepo.deleteByExternalId(holdingId, contributionExternalId) == 0) {
            throw NotFoundException("Contribution not found: $contributionExternalId")
        }
    }
}
