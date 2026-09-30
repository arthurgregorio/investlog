package br.com.investlog.server.results.services

import br.com.investlog.server.jooq.finances.enums.ResultType
import br.com.investlog.server.results.repositories.HoldingPosition
import br.com.investlog.server.results.repositories.RecordedResult
import br.com.investlog.server.results.repositories.ResultRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class PositionExitService(private val resultRepository: ResultRepository) {

    companion object {
        private const val COST_BASIS_SCALE = 10
    }

    @Transactional
    fun exitHolding(
        position: HoldingPosition,
        resultType: ResultType,
        resultDate: LocalDate,
        quantity: BigDecimal,
        unitPrice: BigDecimal,
        fees: BigDecimal,
        taxes: BigDecimal,
    ): RecordedResult {
        val remainingQuantity = position.quantity ?: BigDecimal.ZERO

        val costBasis = position.costBasis
            .divide(remainingQuantity, COST_BASIS_SCALE, RoundingMode.HALF_UP)
            .multiply(quantity)

        val result = resultRepository.insert(
            position = position,
            resultType = resultType,
            resultDate = resultDate,
            quantity = quantity,
            grossAmount = quantity.multiply(unitPrice),
            fees = fees,
            taxes = taxes,
            costBasis = costBasis,
        )

        if (quantity.compareTo(remainingQuantity) == 0) {
            resultRepository.markCompleted(position)
        }

        return result
    }

    @Transactional
    fun exitFund(
        position: HoldingPosition,
        resultType: ResultType,
        resultDate: LocalDate,
        amount: BigDecimal,
        fees: BigDecimal,
        taxes: BigDecimal,
    ): RecordedResult {
        val currentValue = position.currentValue ?: BigDecimal.ZERO

        val costBasis = position.costBasis
            .multiply(amount)
            .divide(currentValue, COST_BASIS_SCALE, RoundingMode.HALF_UP)

        val result = resultRepository.insert(
            position = position,
            resultType = resultType,
            resultDate = resultDate,
            quantity = null,
            grossAmount = amount,
            fees = fees,
            taxes = taxes,
            costBasis = costBasis,
        )

        resultRepository.reduceFundCurrentValue(position.holdingId, amount)

        if (amount.compareTo(currentValue) == 0) {
            resultRepository.markCompleted(position)
        }

        return result
    }
}
