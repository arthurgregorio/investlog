package br.com.investlog.server.strategies.services

import br.com.investlog.server.currencyrates.repositories.CurrencyRateRepository
import br.com.investlog.server.shared.exceptions.InvalidStrategyException
import br.com.investlog.server.shared.exceptions.NotFoundException
import br.com.investlog.server.shared.security.CurrentUser
import br.com.investlog.server.shared.security.CurrentUserProvider
import br.com.investlog.server.strategies.repositories.StrategyAllocationRepository
import br.com.investlog.server.strategies.repositories.StrategyClassRepository
import br.com.investlog.server.strategies.repositories.StrategyRepository
import br.com.investlog.server.strategies.repositories.StrategyRow
import br.com.investlog.server.strategies.repositories.ValuedHoldingRow
import br.com.investlog.server.strategies.rest.payloads.AllocationGroupBy
import br.com.investlog.server.strategies.rest.payloads.AllocationGroupResponse
import br.com.investlog.server.strategies.rest.payloads.ClassAllocationResponse
import br.com.investlog.server.strategies.rest.payloads.HoldingAllocationResponse
import br.com.investlog.server.strategies.rest.payloads.OverviewAllocationResponse
import br.com.investlog.server.strategies.rest.payloads.SegmentAllocationResponse
import br.com.investlog.server.strategies.rest.payloads.StrategyAllocationResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@Service
@Transactional(readOnly = true)
class StrategyAllocationService(
    private val currentUserProvider: CurrentUserProvider,
    private val currencyRateRepository: CurrencyRateRepository,
    private val strategyRepository: StrategyRepository,
    private val strategyClassRepository: StrategyClassRepository,
    private val strategyAllocationRepository: StrategyAllocationRepository,
) {

    private data class HoldingGroup(val id: UUID?, val label: String, val holdings: List<ValuedHoldingRow>)

    fun findAllocation(strategyExternalId: UUID): StrategyAllocationResponse {
        val portfolio = portfolio(strategyExternalId)
        return StrategyAllocationResponse(
            strategyId = portfolio.strategy.externalId,
            strategyName = portfolio.strategy.name,
            displayCurrency = portfolio.displayCurrency,
            totalValue = Shares.money(portfolio.value),
            classes = portfolio.classes.map { strategyClass -> toClassAllocation(strategyClass, portfolio.value) },
            unassignedHoldingCount = portfolio.unassigned.size,
            unassignedValue = Shares.money(portfolio.unassigned.totalValue()),
        )
    }

    fun portfolio(strategyExternalId: UUID): StrategyPortfolio {
        val user = currentUserProvider.getCurrentUser()
        val strategy = findStrategy(user.id, strategyExternalId)
        if (strategy.isDefault) throw InvalidStrategyException("A estratégia padrão não tem metas")

        val holdingsByClass = activeHoldings(user).groupBy { holding -> holding.strategyClassId }
        val classes = strategyClassRepository.findByStrategies(listOf(strategy.id)).map { strategyClass ->
            ClassPortfolio(strategyClass, holdingsByClass[strategyClass.id].orEmpty())
        }
        val unassigned = holdingsByClass[strategyClassRepository.findDefaultClassId(user.id)].orEmpty()
        return StrategyPortfolio(strategy, user.preferredCurrency, classes, unassigned)
    }

    fun findOverviewAllocation(groupBy: AllocationGroupBy, strategyExternalId: UUID?): OverviewAllocationResponse {
        val user = currentUserProvider.getCurrentUser()
        val holdings = activeHoldings(user)
        val groups = when (groupBy) {
            AllocationGroupBy.STRATEGY -> groupByStrategy(user.id, holdings)
            AllocationGroupBy.CLASS -> groupByClass(user.id, holdings, strategyExternalId)
            AllocationGroupBy.TYPE -> holdings.groupBy { holding -> holding.typeLabel ?: "Sem tipo" }
                .map { (label, members) -> HoldingGroup(null, label, members) }
        }
        val totalValue = groups.fold(BigDecimal.ZERO) { total, group -> total + group.holdings.totalValue() }

        return OverviewAllocationResponse(
            groupBy = groupBy,
            displayCurrency = user.preferredCurrency,
            totalValue = Shares.money(totalValue),
            groups = groups
                .map { group ->
                    val value = group.holdings.totalValue()
                    AllocationGroupResponse(
                        id = group.id,
                        label = group.label,
                        holdingCount = group.holdings.size,
                        value = Shares.money(value),
                        percentage = Shares.percentage(value, totalValue),
                    )
                }
                .sortedWith(compareByDescending<AllocationGroupResponse> { group -> group.value }.thenBy { group -> group.label }),
        )
    }

    private fun groupByStrategy(userId: Long, holdings: List<ValuedHoldingRow>): List<HoldingGroup> {
        val strategies = strategyRepository.findAll(userId).associateBy { strategy -> strategy.id }
        val strategyIdByClass = strategyClassRepository.findByStrategies(strategies.keys)
            .associate { strategyClass -> strategyClass.id to strategyClass.strategyId }
        return holdings.groupBy { holding -> strategyIdByClass.getValue(holding.strategyClassId) }
            .map { (strategyId, members) ->
                val strategy = strategies.getValue(strategyId)
                HoldingGroup(strategy.externalId, strategy.name, members)
            }
    }

    private fun groupByClass(userId: Long, holdings: List<ValuedHoldingRow>, strategyExternalId: UUID?): List<HoldingGroup> {
        val strategy = findStrategy(userId, strategyExternalId ?: throw InvalidStrategyException("Informe a estratégia para agrupar por classe"))
        val classes = strategyClassRepository.findByStrategies(listOf(strategy.id)).associateBy { strategyClass -> strategyClass.id }
        return holdings.filter { holding -> holding.strategyClassId in classes }
            .groupBy { holding -> holding.strategyClassId }
            .map { (classId, members) ->
                val strategyClass = classes.getValue(classId)
                HoldingGroup(strategyClass.externalId, strategyClass.name, members)
            }
    }

    private fun toClassAllocation(strategyClass: ClassPortfolio, strategyValue: BigDecimal): ClassAllocationResponse {
        val actualPercentage = Shares.percentage(strategyClass.value, strategyValue)
        return ClassAllocationResponse(
            id = strategyClass.strategyClass.externalId,
            name = strategyClass.strategyClass.name,
            targetPercentage = strategyClass.target,
            actualPercentage = actualPercentage,
            driftPercentagePoints = actualPercentage - strategyClass.target,
            driftValue = Shares.money(strategyClass.value - Shares.valueOf(strategyClass.target, strategyValue)),
            value = Shares.money(strategyClass.value),
            holdings = strategyClass.holdings.mapIndexed { index, holding ->
                toHoldingAllocation(holding, strategyClass.effectiveTargets[index], strategyClass.value)
            },
            segments = strategyClass.holdings.groupBy { holding -> holding.segmentLabel ?: "Sem segmento" }
                .map { (label, members) ->
                    val value = members.totalValue()
                    SegmentAllocationResponse(
                        label = label,
                        value = Shares.money(value),
                        classPercentage = Shares.percentage(value, strategyClass.value),
                        strategyPercentage = Shares.percentage(value, strategyValue),
                    )
                }
                .sortedWith(compareByDescending<SegmentAllocationResponse> { segment -> segment.value }.thenBy { segment -> segment.label }),
        )
    }

    private fun toHoldingAllocation(holding: ValuedHoldingRow, target: BigDecimal, classValue: BigDecimal): HoldingAllocationResponse {
        val actualPercentage = Shares.percentage(holding.value, classValue)
        return HoldingAllocationResponse(
            kind = holding.kind.literal,
            id = holding.externalId,
            walletId = holding.walletExternalId,
            walletName = holding.walletName,
            ticker = holding.ticker,
            name = holding.name,
            typeLabel = holding.typeLabel,
            segmentLabel = holding.segmentLabel,
            frozen = holding.frozen,
            targetPercentage = target,
            typed = holding.targetPercentage != null,
            actualPercentage = actualPercentage,
            driftPercentagePoints = actualPercentage - target,
            driftValue = Shares.money(holding.value - Shares.valueOf(target, classValue)),
            value = Shares.money(holding.value),
        )
    }

    private fun findStrategy(userId: Long, strategyExternalId: UUID): StrategyRow =
        strategyRepository.findByExternalId(userId, strategyExternalId)
            ?: throw NotFoundException("Estratégia não encontrada: $strategyExternalId")

    private fun activeHoldings(user: CurrentUser): List<ValuedHoldingRow> =
        strategyAllocationRepository.findActiveHoldings(user.id, currencyRateRepository.findRateOrAnchor(user.preferredCurrency))
}
