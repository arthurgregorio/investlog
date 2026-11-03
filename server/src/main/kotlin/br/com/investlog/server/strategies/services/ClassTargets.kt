package br.com.investlog.server.strategies.services

import br.com.investlog.server.shared.exceptions.InvalidStrategyException
import java.math.BigDecimal
import java.math.RoundingMode

object ClassTargets {

    private val HUNDRED = BigDecimal("100")
    private const val SHARE_SCALE = 4

    fun blankShare(typedTargets: List<BigDecimal?>): BigDecimal? {
        val blankCount = typedTargets.count { target -> target == null }
        if (blankCount == 0) return null
        val remaining = (HUNDRED - typedSum(typedTargets)).max(BigDecimal.ZERO)
        return remaining.divide(BigDecimal(blankCount), SHARE_SCALE, RoundingMode.HALF_EVEN)
    }

    fun effective(typedTargets: List<BigDecimal?>): List<BigDecimal> {
        val share = blankShare(typedTargets)
        return typedTargets.map { target -> target ?: share!! }
    }

    fun typedSum(typedTargets: List<BigDecimal?>): BigDecimal =
        typedTargets.filterNotNull().fold(BigDecimal.ZERO, BigDecimal::add)

    fun validate(typedTargets: List<BigDecimal?>) {
        val sum = typedSum(typedTargets)
        if (sum > HUNDRED) {
            throw InvalidStrategyException("As metas dos investimentos passam de 100% da classe (soma: ${sum.stripTrailingZeros().toPlainString()}%)")
        }
        val everyTargetTyped = typedTargets.isNotEmpty() && typedTargets.all { target -> target != null }
        if (everyTargetTyped && sum.compareTo(HUNDRED) != 0) {
            throw InvalidStrategyException("Quando todos os investimentos têm meta, elas precisam somar 100% da classe (soma: ${sum.stripTrailingZeros().toPlainString()}%)")
        }
    }

    fun validateClassTargets(classTargets: List<BigDecimal>) {
        val sum = classTargets.fold(BigDecimal.ZERO, BigDecimal::add)
        if (sum.compareTo(HUNDRED) != 0) {
            throw InvalidStrategyException("As metas das classes precisam somar 100% (soma: ${sum.stripTrailingZeros().toPlainString()}%)")
        }
    }
}
