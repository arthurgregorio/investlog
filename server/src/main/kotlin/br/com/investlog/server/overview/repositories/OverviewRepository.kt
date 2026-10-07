package br.com.investlog.server.overview.repositories

import br.com.investlog.server.jooq.finances.tables.references.CONTRIBUTIONS_TIMELINE
import br.com.investlog.server.jooq.finances.tables.references.CURRENCY_RATES
import br.com.investlog.server.jooq.finances.enums.HoldingStatus
import br.com.investlog.server.jooq.finances.tables.references.HOLDINGS_VALUED
import br.com.investlog.server.overview.rest.payloads.KindSummaryResponse
import br.com.investlog.server.overview.rest.payloads.PortfolioSummaryResponse
import br.com.investlog.server.overview.rest.payloads.SeriesPointResponse
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.math.RoundingMode

@Repository
class OverviewRepository(private val dsl: DSLContext) {

    fun findSummary(userId: Long, displayCurrency: String): PortfolioSummaryResponse {
        val overview = HOLDINGS_VALUED.`as`("overview")

        val appliedRate = overview.RATE.div(displayCurrencyRate(displayCurrency))

        val kindSummaries = dsl.select(
            overview.KIND,
            DSL.count().`as`("holding_count"),
            DSL.coalesce(DSL.sum(overview.COST_BASIS.mul(appliedRate)), BigDecimal.ZERO).`as`("total_cost_basis"),
            DSL.coalesce(DSL.sum(overview.CURRENT_VALUE.mul(appliedRate)), BigDecimal.ZERO).`as`("total_current_value"),
        )
            .from(overview)
            .where(overview.USER_ID.eq(userId))
            .and(overview.STATUS.eq(HoldingStatus.ACTIVE))
            .groupBy(overview.KIND)
            .fetch { record ->
                val totalCostBasis = record.get("total_cost_basis", BigDecimal::class.java) ?: BigDecimal.ZERO
                val totalCurrentValue = record.get("total_current_value", BigDecimal::class.java) ?: BigDecimal.ZERO
                val totalGain = totalCurrentValue - totalCostBasis

                KindSummaryResponse(
                    kind = record.get(overview.KIND)!!.literal,
                    holdingCount = record.get("holding_count", Int::class.java) ?: 0,
                    totalCostBasis = totalCostBasis,
                    totalCurrentValue = totalCurrentValue,
                    totalGain = totalGain,
                    totalGainPct = gainPct(totalGain, totalCostBasis),
                )
            }

        val totalCostBasis = kindSummaries.fold(BigDecimal.ZERO) { acc, kind -> acc + kind.totalCostBasis }
        val totalCurrentValue = kindSummaries.fold(BigDecimal.ZERO) { acc, kind -> acc + kind.totalCurrentValue }
        val totalGain = totalCurrentValue - totalCostBasis

        return PortfolioSummaryResponse(
            displayCurrency = displayCurrency,
            totalCostBasis = totalCostBasis,
            totalCurrentValue = totalCurrentValue,
            totalGain = totalGain,
            totalGainPct = gainPct(totalGain, totalCostBasis),
            kindSummaries = kindSummaries,
        )
    }

    fun findSeries(userId: Long, displayCurrency: String): List<SeriesPointResponse> {
        val timeline = CONTRIBUTIONS_TIMELINE.`as`("timeline")

        val monthlyAmount = DSL.sum(timeline.WALLET_AMOUNT.mul(timeline.RATE.div(displayCurrencyRate(displayCurrency))))
        val cumulativeAmount = DSL.sum(monthlyAmount).over().orderBy(timeline.MONTH)

        return dsl.select(
            DSL.field("TO_CHAR({0}, 'YYYY-MM')", SQLDataType.VARCHAR, timeline.MONTH).`as`("month"),
            cumulativeAmount.`as`("total_invested"),
        )
            .from(timeline)
            .where(timeline.USER_ID.eq(userId))
            .groupBy(timeline.MONTH)
            .orderBy(timeline.MONTH)
            .fetch { record ->
                SeriesPointResponse(
                    month = record.get("month", String::class.java)!!,
                    totalInvested = record.get("total_invested", BigDecimal::class.java)!!,
                )
            }
    }

    private fun displayCurrencyRate(displayCurrency: String) = DSL.coalesce(
        DSL.field(
            DSL.select(CURRENCY_RATES.RATE)
                .from(CURRENCY_RATES)
                .where(CURRENCY_RATES.CURRENCY_CODE.eq(displayCurrency))
        ),
        BigDecimal.ONE,
    )

    private fun gainPct(gain: BigDecimal, costBasis: BigDecimal): BigDecimal? =
        if (costBasis.signum() != 0) gain.divide(costBasis, 10, RoundingMode.HALF_UP).multiply(BigDecimal("100"))
        else null
}
