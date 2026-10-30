package br.com.investlog.server.strategies.services

import br.com.investlog.server.shared.exceptions.InvalidStrategyException
import br.com.investlog.server.strategies.repositories.ValuedHoldingRow
import br.com.investlog.server.strategies.rest.payloads.ClassContributionResponse
import br.com.investlog.server.strategies.rest.payloads.ContributionPlanResponse
import br.com.investlog.server.strategies.rest.payloads.HoldingContributionResponse
import br.com.investlog.server.strategies.rest.payloads.LeftOutHoldingResponse
import br.com.investlog.server.strategies.rest.payloads.LeftOutReason
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

@Service
@Transactional(readOnly = true)
class ContributionPlannerService(private val strategyAllocationService: StrategyAllocationService) {

    private data class ClassShare(val strategyClass: ClassPortfolio, val amount: BigDecimal)

    private data class HoldingGap(val holding: ValuedHoldingRow, val gap: BigDecimal)

    fun plan(strategyExternalId: UUID, requestedAmount: BigDecimal): ContributionPlanResponse {
        val amount = requestedAmount.setScale(2, RoundingMode.HALF_EVEN)
        if (amount.signum() <= 0) throw InvalidStrategyException("O valor do aporte precisa ser maior que zero")

        val portfolio = strategyAllocationService.portfolio(strategyExternalId)
        val totalAfter = portfolio.value + amount
        val belowTarget = portfolio.classes.filter { strategyClass -> classGap(strategyClass, totalAfter).signum() > 0 }
        val receiving = receivingClasses(belowTarget, amount, totalAfter)
        if (receiving.isEmpty()) throw InvalidStrategyException("Nenhuma classe da estratégia pode receber este aporte")

        val eligibleByClass = receiving.associate { share ->
            share.strategyClass.strategyClass.id to eligibleGaps(share.strategyClass, share.amount)
        }
        return ContributionPlanResponse(
            strategyId = portfolio.strategy.externalId,
            displayCurrency = portfolio.displayCurrency,
            amount = amount,
            totalValueBefore = Shares.money(portfolio.value),
            totalValueAfter = Shares.money(totalAfter),
            classes = receiving.map { share ->
                toClassContribution(share, eligibleByClass.getValue(share.strategyClass.strategyClass.id), portfolio.value, totalAfter)
            },
            leftOut = portfolio.classes.flatMap { strategyClass ->
                val eligibleIds = eligibleByClass[strategyClass.strategyClass.id].orEmpty().map { holdingGap -> holdingGap.holding.externalId }.toSet()
                val classAboveTarget = classGap(strategyClass, totalAfter).signum() <= 0
                strategyClass.holdings
                    .filterNot { holding -> holding.externalId in eligibleIds }
                    .map { holding -> toLeftOut(holding, strategyClass, leftOutReason(holding, classAboveTarget)) }
            },
        )
    }

    private fun receivingClasses(candidates: List<ClassPortfolio>, amount: BigDecimal, totalAfter: BigDecimal): List<ClassShare> {
        var remaining = candidates
        while (remaining.isNotEmpty()) {
            val shares = remaining.zip(Shares.split(amount, remaining.map { strategyClass -> classGap(strategyClass, totalAfter) }))
                .map { (strategyClass, classAmount) -> ClassShare(strategyClass, classAmount) }
            val stuck = shares.filter { share -> eligibleGaps(share.strategyClass, share.amount).isEmpty() }
            if (stuck.isEmpty()) return shares
            remaining = remaining - stuck.map { share -> share.strategyClass }.toSet()
        }
        return emptyList()
    }

    private fun classGap(strategyClass: ClassPortfolio, totalAfter: BigDecimal): BigDecimal =
        Shares.valueOf(strategyClass.target, totalAfter) - strategyClass.value

    private fun eligibleGaps(strategyClass: ClassPortfolio, classAmount: BigDecimal): List<HoldingGap> {
        val classValueAfter = strategyClass.value + classAmount
        return strategyClass.holdings.mapIndexed { index, holding ->
            HoldingGap(holding, Shares.valueOf(strategyClass.effectiveTargets[index], classValueAfter) - holding.value)
        }.filter { holdingGap -> !holdingGap.holding.frozen && holdingGap.gap.signum() > 0 }
    }

    private fun leftOutReason(holding: ValuedHoldingRow, classAboveTarget: Boolean): LeftOutReason = when {
        holding.frozen -> LeftOutReason.FROZEN
        classAboveTarget -> LeftOutReason.CLASS_ABOVE_TARGET
        else -> LeftOutReason.ABOVE_TARGET_IN_CLASS
    }

    private fun toClassContribution(
        share: ClassShare,
        eligible: List<HoldingGap>,
        totalBefore: BigDecimal,
        totalAfter: BigDecimal,
    ): ClassContributionResponse {
        val strategyClass = share.strategyClass
        val holdingAmounts = Shares.split(share.amount, eligible.map { holdingGap -> holdingGap.gap })
        return ClassContributionResponse(
            id = strategyClass.strategyClass.externalId,
            name = strategyClass.strategyClass.name,
            targetPercentage = strategyClass.target,
            percentageBefore = Shares.percentage(strategyClass.value, totalBefore),
            percentageAfter = Shares.percentage(strategyClass.value + share.amount, totalAfter),
            amount = share.amount,
            holdings = eligible.zip(holdingAmounts).map { (holdingGap, holdingAmount) ->
                HoldingContributionResponse(
                    kind = holdingGap.holding.kind.literal,
                    id = holdingGap.holding.externalId,
                    walletId = holdingGap.holding.walletExternalId,
                    walletName = holdingGap.holding.walletName,
                    ticker = holdingGap.holding.ticker,
                    name = holdingGap.holding.name,
                    amount = holdingAmount,
                )
            },
        )
    }

    private fun toLeftOut(holding: ValuedHoldingRow, strategyClass: ClassPortfolio, reason: LeftOutReason): LeftOutHoldingResponse =
        LeftOutHoldingResponse(
            kind = holding.kind.literal,
            id = holding.externalId,
            walletId = holding.walletExternalId,
            walletName = holding.walletName,
            ticker = holding.ticker,
            name = holding.name,
            className = strategyClass.strategyClass.name,
            reason = reason,
        )
}
