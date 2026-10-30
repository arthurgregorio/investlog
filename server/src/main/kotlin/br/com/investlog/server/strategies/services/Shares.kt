package br.com.investlog.server.strategies.services

import java.math.BigDecimal
import java.math.RoundingMode

object Shares {

    private val HUNDRED = BigDecimal("100")
    private const val SCALE = 2

    fun percentage(part: BigDecimal, whole: BigDecimal): BigDecimal =
        if (whole.signum() == 0) BigDecimal.ZERO.setScale(SCALE)
        else part.multiply(HUNDRED).divide(whole, SCALE, RoundingMode.HALF_EVEN)

    fun valueOf(percentage: BigDecimal, whole: BigDecimal): BigDecimal =
        whole.multiply(percentage).movePointLeft(2)

    fun money(value: BigDecimal): BigDecimal = value.setScale(SCALE, RoundingMode.HALF_EVEN)

    fun split(amount: BigDecimal, weights: List<BigDecimal>): List<BigDecimal> {
        val weightSum = weights.fold(BigDecimal.ZERO, BigDecimal::add)
        val leading = weights.dropLast(1).map { weight -> amount.multiply(weight).divide(weightSum, SCALE, RoundingMode.DOWN) }
        return leading + (amount - leading.fold(BigDecimal.ZERO, BigDecimal::add))
    }
}
