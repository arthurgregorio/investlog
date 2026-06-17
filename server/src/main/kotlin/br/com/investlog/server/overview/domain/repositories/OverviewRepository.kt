package br.com.investlog.server.overview.domain.repositories

import br.com.investlog.server.jooq.finances.tables.references.CRYPTO_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.CRYPTO_LOTS
import br.com.investlog.server.jooq.finances.tables.references.CURRENCY_RATES
import br.com.investlog.server.jooq.finances.tables.references.FUND_CONTRIBUTIONS
import br.com.investlog.server.jooq.finances.tables.references.FUND_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.HOLDINGS_OVERVIEW
import br.com.investlog.server.jooq.finances.tables.references.STOCK_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.STOCK_LOTS
import br.com.investlog.server.jooq.finances.tables.references.WALLETS
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

    fun findSummary(userId: Long): PortfolioSummaryResponse {
        val overview = HOLDINGS_OVERVIEW.`as`("overview")
        val wallets = WALLETS.`as`("wallets")
        val currencyRates = CURRENCY_RATES.`as`("currency_rates")

        val baseCurrency = dsl.select(CURRENCY_RATES.CURRENCY_CODE)
            .from(CURRENCY_RATES)
            .where(CURRENCY_RATES.USER_ID.eq(userId))
            .and(CURRENCY_RATES.IS_BASE.isTrue())
            .fetchOne(CURRENCY_RATES.CURRENCY_CODE)

        val appliedRate = DSL.coalesce(currencyRates.RATE, BigDecimal.ONE)

        val kindSummaries = dsl.select(
            overview.KIND,
            DSL.count().`as`("holding_count"),
            DSL.coalesce(DSL.sum(overview.COST_BASIS.mul(appliedRate)), BigDecimal.ZERO).`as`("total_cost_basis"),
            DSL.coalesce(DSL.sum(overview.CURRENT_VALUE.mul(appliedRate)), BigDecimal.ZERO).`as`("total_current_value"),
        )
            .from(overview)
            .join(wallets).on(wallets.ID.eq(overview.WALLET_ID))
            .leftJoin(currencyRates)
                .on(currencyRates.USER_ID.eq(wallets.USER_ID))
                .and(currencyRates.CURRENCY_CODE.eq(wallets.CURRENCY))
            .where(wallets.USER_ID.eq(userId))
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
            baseCurrency = baseCurrency,
            totalCostBasis = totalCostBasis,
            totalCurrentValue = totalCurrentValue,
            totalGain = totalGain,
            totalGainPct = gainPct(totalGain, totalCostBasis),
            kindSummaries = kindSummaries,
        )
    }

    fun findSeries(userId: Long): List<SeriesPointResponse> {
        val stockRates = CURRENCY_RATES.`as`("stock_rates")
        val cryptoRates = CURRENCY_RATES.`as`("crypto_rates")
        val fundRates = CURRENCY_RATES.`as`("fund_rates")
        val stockWallets = WALLETS.`as`("stock_wallets")
        val cryptoWallets = WALLETS.`as`("crypto_wallets")
        val fundWallets = WALLETS.`as`("fund_wallets")

        data class MonthAmount(val month: String, val amount: BigDecimal)

        val stockAmounts = dsl.select(
            DSL.field("TO_CHAR({0}, 'YYYY-MM')", SQLDataType.VARCHAR, STOCK_LOTS.LOT_DATE).`as`("month"),
            STOCK_LOTS.QUANTITY.mul(STOCK_LOTS.PRICE)
                .mul(DSL.coalesce(stockRates.RATE, BigDecimal.ONE)).`as`("amount"),
        )
            .from(STOCK_LOTS)
            .join(STOCK_HOLDINGS).on(STOCK_HOLDINGS.ID.eq(STOCK_LOTS.STOCK_HOLDING_ID))
            .join(stockWallets).on(stockWallets.ID.eq(STOCK_HOLDINGS.WALLET_ID))
            .leftJoin(stockRates)
                .on(stockRates.USER_ID.eq(stockWallets.USER_ID))
                .and(stockRates.CURRENCY_CODE.eq(stockWallets.CURRENCY))
            .where(stockWallets.USER_ID.eq(userId))
            .fetch { MonthAmount(it.get("month", String::class.java)!!, it.get("amount", BigDecimal::class.java) ?: BigDecimal.ZERO) }

        val cryptoAmounts = dsl.select(
            DSL.field("TO_CHAR({0}, 'YYYY-MM')", SQLDataType.VARCHAR, CRYPTO_LOTS.LOT_DATE).`as`("month"),
            CRYPTO_LOTS.QUANTITY.mul(CRYPTO_LOTS.PRICE)
                .mul(DSL.coalesce(cryptoRates.RATE, BigDecimal.ONE)).`as`("amount"),
        )
            .from(CRYPTO_LOTS)
            .join(CRYPTO_HOLDINGS).on(CRYPTO_HOLDINGS.ID.eq(CRYPTO_LOTS.CRYPTO_HOLDING_ID))
            .join(cryptoWallets).on(cryptoWallets.ID.eq(CRYPTO_HOLDINGS.WALLET_ID))
            .leftJoin(cryptoRates)
                .on(cryptoRates.USER_ID.eq(cryptoWallets.USER_ID))
                .and(cryptoRates.CURRENCY_CODE.eq(cryptoWallets.CURRENCY))
            .where(cryptoWallets.USER_ID.eq(userId))
            .fetch { MonthAmount(it.get("month", String::class.java)!!, it.get("amount", BigDecimal::class.java) ?: BigDecimal.ZERO) }

        val fundAmounts = dsl.select(
            DSL.field("TO_CHAR({0}, 'YYYY-MM')", SQLDataType.VARCHAR, FUND_CONTRIBUTIONS.CONTRIBUTION_DATE).`as`("month"),
            FUND_CONTRIBUTIONS.AMOUNT
                .mul(DSL.coalesce(fundRates.RATE, BigDecimal.ONE)).`as`("amount"),
        )
            .from(FUND_CONTRIBUTIONS)
            .join(FUND_HOLDINGS).on(FUND_HOLDINGS.ID.eq(FUND_CONTRIBUTIONS.FUND_HOLDING_ID))
            .join(fundWallets).on(fundWallets.ID.eq(FUND_HOLDINGS.WALLET_ID))
            .leftJoin(fundRates)
                .on(fundRates.USER_ID.eq(fundWallets.USER_ID))
                .and(fundRates.CURRENCY_CODE.eq(fundWallets.CURRENCY))
            .where(fundWallets.USER_ID.eq(userId))
            .fetch { MonthAmount(it.get("month", String::class.java)!!, it.get("amount", BigDecimal::class.java) ?: BigDecimal.ZERO) }

        val grouped = (stockAmounts + cryptoAmounts + fundAmounts)
            .groupBy { it.month }
            .mapValues { entry -> entry.value.fold(BigDecimal.ZERO) { acc, point -> acc + point.amount } }
            .toSortedMap()

        var cumulative = BigDecimal.ZERO
        return grouped.map { (month, total) ->
            cumulative += total
            SeriesPointResponse(month = month, totalInvested = cumulative)
        }
    }

    private fun gainPct(gain: BigDecimal, costBasis: BigDecimal): BigDecimal? =
        if (costBasis.signum() != 0) gain.divide(costBasis, 10, RoundingMode.HALF_UP).multiply(BigDecimal("100"))
        else null
}
