package br.com.investlog.server.strategies.services

import br.com.investlog.server.shared.exceptions.InvalidStrategyException
import br.com.investlog.server.shared.exceptions.NotFoundException
import br.com.investlog.server.shared.security.CurrentUserProvider
import br.com.investlog.server.strategies.repositories.HoldingClassRepository
import br.com.investlog.server.strategies.repositories.StrategyClassRepository
import br.com.investlog.server.strategies.repositories.StrategyClassRow
import br.com.investlog.server.strategies.repositories.StrategyRepository
import br.com.investlog.server.strategies.repositories.StrategyRow
import br.com.investlog.server.strategies.rest.payloads.StrategyClassResponse
import br.com.investlog.server.strategies.rest.payloads.StrategyClassesRequest
import br.com.investlog.server.strategies.rest.payloads.StrategyResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@Service
@Transactional(readOnly = true)
class StrategyService(
    private val currentUserProvider: CurrentUserProvider,
    private val strategyRepository: StrategyRepository,
    private val strategyClassRepository: StrategyClassRepository,
    private val holdingClassRepository: HoldingClassRepository,
) {

    fun findAll(): List<StrategyResponse> {
        val userId = currentUserProvider.getCurrentUser().id
        return toResponses(strategyRepository.findAll(userId))
    }

    @Transactional
    fun create(name: String): StrategyResponse {
        val userId = currentUserProvider.getCurrentUser().id
        val strategyId = strategyRepository.create(userId, name.trim())
        return toResponses(strategyRepository.findAll(userId).filter { strategy -> strategy.id == strategyId }).single()
    }

    @Transactional
    fun rename(externalId: UUID, name: String): StrategyResponse {
        val strategy = editableStrategy(externalId, "A estratégia padrão não pode ser renomeada")
        strategyRepository.rename(strategy.id, name.trim())
        return toResponses(listOf(strategy.copy(name = name.trim()))).single()
    }

    @Transactional
    fun delete(externalId: UUID) {
        val strategy = editableStrategy(externalId, "A estratégia padrão não pode ser excluída")
        val userId = currentUserProvider.getCurrentUser().id
        val classIds = strategyClassRepository.findByStrategies(listOf(strategy.id)).map { strategyClass -> strategyClass.id }
        holdingClassRepository.moveAll(classIds, strategyClassRepository.findDefaultClassId(userId))
        strategyRepository.delete(strategy.id)
    }

    @Transactional
    fun replaceClasses(externalId: UUID, request: StrategyClassesRequest): StrategyResponse {
        val strategy = editableStrategy(externalId, "A estratégia padrão não tem classes editáveis")
        val userId = currentUserProvider.getCurrentUser().id
        val requested = request.classes.map { requestedClass -> requestedClass.copy(name = requestedClass.name.trim()) }

        val repeatedNames = requested.groupBy { requestedClass -> requestedClass.name.lowercase() }.filterValues { group -> group.size > 1 }
        if (repeatedNames.isNotEmpty()) {
            throw InvalidStrategyException("Há classes com o mesmo nome: ${repeatedNames.values.joinToString { group -> group.first().name }}")
        }
        ClassTargets.validateClassTargets(requested.map { requestedClass -> requestedClass.targetPercentage })

        val existing = strategyClassRepository.findByStrategies(listOf(strategy.id)).associateBy { strategyClass -> strategyClass.externalId }
        val keptIds = requested.mapNotNull { requestedClass ->
            requestedClass.id?.let { classExternalId ->
                existing[classExternalId]?.id ?: throw NotFoundException("Classe não encontrada: $classExternalId")
            }
        }
        val removedIds = existing.values.map { strategyClass -> strategyClass.id }.filterNot { classId -> classId in keptIds }

        holdingClassRepository.moveAll(removedIds, strategyClassRepository.findDefaultClassId(userId))
        strategyClassRepository.delete(removedIds)
        strategyClassRepository.releaseNames(keptIds)

        requested.forEachIndexed { position, requestedClass ->
            val existingClass = requestedClass.id?.let(existing::getValue)
            if (existingClass != null) {
                strategyClassRepository.update(existingClass.id, requestedClass.name, requestedClass.targetPercentage, position)
            } else {
                strategyClassRepository.insert(strategy.id, requestedClass.name, requestedClass.targetPercentage, position)
            }
        }
        strategyRepository.touch(strategy.id)

        return toResponses(listOf(strategy)).single()
    }

    private fun editableStrategy(externalId: UUID, defaultMessage: String): StrategyRow {
        val userId = currentUserProvider.getCurrentUser().id
        val strategy = strategyRepository.findByExternalId(userId, externalId)
            ?: throw NotFoundException("Estratégia não encontrada: $externalId")
        if (strategy.isDefault) throw InvalidStrategyException(defaultMessage)
        return strategy
    }

    private fun toResponses(strategies: List<StrategyRow>): List<StrategyResponse> {
        if (strategies.isEmpty()) return emptyList()
        val classesByStrategy = strategyClassRepository.findByStrategies(strategies.map { strategy -> strategy.id })
            .groupBy { strategyClass -> strategyClass.strategyId }
        val classIds = classesByStrategy.values.flatten().map { strategyClass -> strategyClass.id }
        val targetsByClass = holdingClassRepository.findActiveTargets(classIds)
            .groupBy({ holding -> holding.strategyClassId }, { holding -> holding.targetPercentage })

        return strategies.map { strategy ->
            val classes = classesByStrategy[strategy.id].orEmpty().map { strategyClass ->
                toClassResponse(strategyClass, targetsByClass[strategyClass.id].orEmpty())
            }
            StrategyResponse(
                id = strategy.externalId,
                name = strategy.name,
                default = strategy.isDefault,
                holdingCount = classes.sumOf { strategyClass -> strategyClass.holdingCount },
                classes = classes,
            )
        }
    }

    private fun toClassResponse(strategyClass: StrategyClassRow, targets: List<BigDecimal?>): StrategyClassResponse =
        StrategyClassResponse(
            id = strategyClass.externalId,
            name = strategyClass.name,
            targetPercentage = strategyClass.targetPercentage,
            position = strategyClass.position,
            default = strategyClass.isDefault,
            holdingCount = targets.size,
            typedCount = targets.count { target -> target != null },
            typedPercentageSum = ClassTargets.typedSum(targets),
            blankSharePercentage = ClassTargets.blankShare(targets),
        )
}
