package br.com.investlog.server.strategies.services

import br.com.investlog.server.strategies.repositories.StrategyClassRow
import br.com.investlog.server.strategies.repositories.StrategyRow
import br.com.investlog.server.strategies.repositories.ValuedHoldingRow
import java.math.BigDecimal

data class ClassPortfolio(
    val strategyClass: StrategyClassRow,
    val holdings: List<ValuedHoldingRow>,
) {
    val target: BigDecimal = strategyClass.targetPercentage!!
    val value: BigDecimal = holdings.totalValue()
    val effectiveTargets: List<BigDecimal> = ClassTargets.effective(holdings.map { holding -> holding.targetPercentage })
}

data class StrategyPortfolio(
    val strategy: StrategyRow,
    val displayCurrency: String,
    val classes: List<ClassPortfolio>,
    val unassigned: List<ValuedHoldingRow>,
) {
    val value: BigDecimal = classes.fold(BigDecimal.ZERO) { total, strategyClass -> total + strategyClass.value }
}

fun Collection<ValuedHoldingRow>.totalValue(): BigDecimal =
    fold(BigDecimal.ZERO) { total, holding -> total + holding.value }
