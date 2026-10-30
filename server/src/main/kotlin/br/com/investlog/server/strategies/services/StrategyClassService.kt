package br.com.investlog.server.strategies.services

import br.com.investlog.server.shared.exceptions.InvalidStrategyException
import br.com.investlog.server.shared.exceptions.NotFoundException
import br.com.investlog.server.shared.security.CurrentUserProvider
import br.com.investlog.server.strategies.repositories.HoldingClassRepository
import br.com.investlog.server.strategies.repositories.StrategyClassRepository
import br.com.investlog.server.strategies.repositories.StrategyClassRow
import br.com.investlog.server.strategies.rest.payloads.ClassAssignmentRequest
import br.com.investlog.server.strategies.rest.payloads.ClassHoldingResponse
import br.com.investlog.server.strategies.rest.payloads.ClassTargetsRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import br.com.investlog.server.jooq.finances.enums.WalletKind as JooqWalletKind

@Service
@Transactional(readOnly = true)
class StrategyClassService(
    private val currentUserProvider: CurrentUserProvider,
    private val strategyClassRepository: StrategyClassRepository,
    private val holdingClassRepository: HoldingClassRepository,
) {

    fun findHoldings(classExternalId: UUID): List<ClassHoldingResponse> =
        holdingClassRepository.findClassHoldings(findClass(classExternalId).id, ClassTargets::effective)

    @Transactional
    fun updateTargets(classExternalId: UUID, request: ClassTargetsRequest): List<ClassHoldingResponse> {
        val strategyClass = findClass(classExternalId)
        if (strategyClass.isDefault) throw InvalidStrategyException("Não definida não tem metas")

        val holdings = holdingClassRepository.findActiveTargets(listOf(strategyClass.id))
        val holdingsByReference = holdings.associateBy { holding -> holding.kind to holding.holdingExternalId }
        request.targets.forEach { target ->
            if ((target.kind.jooqWalletKind to target.holdingId) !in holdingsByReference) {
                throw NotFoundException("Investimento não encontrado nesta classe: ${target.holdingId}")
            }
        }

        val requestedTargets = request.targets.associate { target -> (target.kind.jooqWalletKind to target.holdingId) to target.targetPercentage }
        val targets = holdings.map { holding -> requestedTargets[holding.kind to holding.holdingExternalId] }
        ClassTargets.validate(targets)

        holdings.zip(targets).forEach { (holding, target) ->
            holdingClassRepository.setTarget(holding.kind, holding.holdingId, target)
        }
        return findHoldings(classExternalId)
    }

    @Transactional
    fun assign(classExternalId: UUID, request: ClassAssignmentRequest) {
        val strategyClass = findClass(classExternalId)
        val userId = currentUserProvider.getCurrentUser().id
        request.holdings.forEach { holding ->
            val holdingId = holdingClassRepository.findInternalId(userId, holding.kind.jooqWalletKind, holding.id)
                ?: throw NotFoundException("Investimento não encontrado: ${holding.id}")
            holdingClassRepository.assign(holding.kind.jooqWalletKind, holdingId, strategyClass.id)
        }
    }

    @Transactional
    fun assignHolding(kind: JooqWalletKind, holdingId: Long, classExternalId: UUID) {
        holdingClassRepository.assign(kind, holdingId, findClass(classExternalId).id)
    }

    fun resolveId(classExternalId: UUID?): Long? = classExternalId?.let { externalId -> findClass(externalId).id }

    private fun findClass(classExternalId: UUID): StrategyClassRow {
        val userId = currentUserProvider.getCurrentUser().id
        return strategyClassRepository.findByExternalId(userId, classExternalId)
            ?: throw NotFoundException("Classe não encontrada: $classExternalId")
    }
}
