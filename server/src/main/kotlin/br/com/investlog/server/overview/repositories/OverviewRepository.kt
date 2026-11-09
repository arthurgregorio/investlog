package br.com.investlog.server.overview.repositories

import br.com.investlog.server.jooq.finances.enums.HoldingStatus
import br.com.investlog.server.jooq.finances.enums.WalletKind
import br.com.investlog.server.jooq.finances.tables.references.CAPITAL_TIMELINE
import br.com.investlog.server.jooq.finances.tables.references.CURRENCY_RATES
import br.com.investlog.server.jooq.finances.tables.references.HOLDINGS_OVERVIEW
import br.com.investlog.server.jooq.finances.tables.references.HOLDINGS_VALUED
import br.com.investlog.server.jooq.finances.tables.references.HOLDING_DAILY_SNAPSHOTS
import br.com.investlog.server.jooq.finances.tables.references.WALLETS
import br.com.investlog.server.jooq.finances.tables.references.WALLET_CASH
import br.com.investlog.server.jooq.finances.tables.references.WALLET_DAILY_SNAPSHOTS
import br.com.investlog.server.overview.rest.payloads.KindSummaryResponse
import br.com.investlog.server.overview.rest.payloads.PortfolioSummaryResponse
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

@Repository
class OverviewRepository(private val dsl: DSLContext) {

    fun findSummary(userId: Long, displayCurrency: String): PortfolioSummaryResponse {
        val overview = HOLDINGS_VALUED.`as`("overview")

        val appliedRate = overview.RATE.div(displayCurrencyRate(displayCurrency))

        val kindSummaries = dsl.select(
            overview.KIND,
            DSL.count().`as`("holding_count"),
            DSL.countDistinct(overview.WALLET_ID).`as`("wallet_count"),
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
                    walletCount = record.get("wallet_count", Int::class.java) ?: 0,
                    totalCostBasis = totalCostBasis,
                    totalCurrentValue = totalCurrentValue,
                    totalGain = totalGain,
                    totalGainPct = gainPct(totalGain, totalCostBasis),
                )
            }

        val totalCostBasis = kindSummaries.fold(BigDecimal.ZERO) { acc, kind -> acc + kind.totalCostBasis }
        val positionsValue = kindSummaries.fold(BigDecimal.ZERO) { acc, kind -> acc + kind.totalCurrentValue }
        val totalGain = positionsValue - totalCostBasis
        val totalCash = findTotalCash(userId, displayCurrency)
        val totalCurrentValue = if (totalCash.signum() == 0) positionsValue else positionsValue + totalCash
        val capital = findCapitalTotals(userId, displayCurrency)
        val lifetimeResult = totalCurrentValue - capital.ownMoney

        return PortfolioSummaryResponse(
            displayCurrency = displayCurrency,
            totalCostBasis = totalCostBasis,
            totalCurrentValue = totalCurrentValue,
            totalCash = totalCash,
            totalGain = totalGain,
            totalGainPct = gainPct(totalGain, totalCostBasis),
            positionsValue = positionsValue,
            ownMoney = capital.ownMoney,
            realizedProfit = capital.reinvestedProfit,
            lifetimeResult = lifetimeResult,
            lifetimeResultPct = gainPct(lifetimeResult, capital.ownMoney),
            unpricedHoldingCount = countUnpricedHoldings(userId),
            cashWalletCount = countWalletsWithCash(userId),
            kindSummaries = kindSummaries,
        )
    }

    fun findCapitalSeries(userId: Long, displayCurrency: String): List<CapitalMonth> {
        val timeline = CAPITAL_TIMELINE.`as`("timeline")
        val appliedRate = timeline.RATE.div(displayCurrencyRate(displayCurrency))

        val cumulativeOwnMoney = DSL.sum(DSL.sum(timeline.OWN_MONEY_AMOUNT.mul(appliedRate)))
            .over().orderBy(timeline.MONTH)
        val cumulativeReinvestedProfit = DSL.sum(DSL.sum(timeline.REINVESTED_PROFIT_AMOUNT.mul(appliedRate)))
            .over().orderBy(timeline.MONTH)

        return dsl.select(
            DSL.field("TO_CHAR({0}, 'YYYY-MM')", SQLDataType.VARCHAR, timeline.MONTH).`as`("month"),
            cumulativeOwnMoney.`as`("own_money"),
            cumulativeReinvestedProfit.`as`("reinvested_profit"),
        )
            .from(timeline)
            .where(timeline.USER_ID.eq(userId))
            .groupBy(timeline.MONTH)
            .orderBy(timeline.MONTH)
            .fetch { record ->
                CapitalMonth(
                    month = YearMonth.parse(record.get("month", String::class.java)!!),
                    ownMoney = record.get("own_money", BigDecimal::class.java)!!.withoutZeroScale(),
                    reinvestedProfit = record.get("reinvested_profit", BigDecimal::class.java)!!.withoutZeroScale(),
                )
            }
    }

    fun findWalletSnapshotGains(userId: Long, displayCurrency: String): List<WalletSnapshotGain> {
        val appliedRate = DSL.coalesce(CURRENCY_RATES.RATE, BigDecimal.ONE).div(displayCurrencyRate(displayCurrency))
        val gain = WALLET_DAILY_SNAPSHOTS.GAIN.mul(appliedRate)

        return dsl.select(WALLET_DAILY_SNAPSHOTS.WALLET_ID, WALLET_DAILY_SNAPSHOTS.SNAPSHOT_DATE, gain)
            .from(WALLET_DAILY_SNAPSHOTS)
            .join(WALLETS).on(WALLETS.ID.eq(WALLET_DAILY_SNAPSHOTS.WALLET_ID))
            .leftJoin(CURRENCY_RATES).on(CURRENCY_RATES.CURRENCY_CODE.eq(WALLETS.CURRENCY))
            .where(WALLETS.USER_ID.eq(userId))
            .orderBy(WALLET_DAILY_SNAPSHOTS.WALLET_ID, WALLET_DAILY_SNAPSHOTS.SNAPSHOT_DATE)
            .fetch { record ->
                WalletSnapshotGain(
                    walletId = record.get(WALLET_DAILY_SNAPSHOTS.WALLET_ID)!!,
                    snapshotDate = record.get(WALLET_DAILY_SNAPSHOTS.SNAPSHOT_DATE)!!,
                    gain = record.get(gain)!!,
                )
            }
    }

    fun findMoverCandidates(userId: Long, monthStart: LocalDate): List<MoverCandidate> {
        val overview = HOLDINGS_VALUED.`as`("overview")
        val baselinePrice = baselinePrice(overview.KIND, overview.HOLDING_ID, monthStart)

        return dsl.select(overview.KIND, overview.TICKER, overview.NAME, overview.CURRENT_PRICE, baselinePrice)
            .from(overview)
            .where(overview.USER_ID.eq(userId))
            .and(overview.STATUS.eq(HoldingStatus.ACTIVE))
            .and(overview.KIND.`in`(WalletKind.STOCKS, WalletKind.CRYPTO))
            .and(overview.CURRENT_PRICE.isNotNull)
            .fetch { record ->
                MoverCandidate(
                    kind = record.get(overview.KIND)!!.literal,
                    ticker = record.get(overview.TICKER),
                    name = record.get(overview.NAME),
                    currentPrice = record.get(overview.CURRENT_PRICE)!!,
                    baselinePrice = record.get(baselinePrice),
                )
            }
    }

    fun findMoverCandidatesOn(userId: Long, monthStart: LocalDate, endDate: LocalDate): List<MoverCandidate> {
        val endSnapshots = HOLDING_DAILY_SNAPSHOTS.`as`("end_snapshots")
        val holdings = HOLDINGS_OVERVIEW.`as`("holdings")
        val baselinePrice = baselinePrice(endSnapshots.KIND, endSnapshots.HOLDING_ID, monthStart)

        return dsl.select(endSnapshots.KIND, holdings.TICKER, holdings.NAME, endSnapshots.CURRENT_PRICE, baselinePrice)
            .from(endSnapshots)
            .join(WALLETS).on(WALLETS.ID.eq(endSnapshots.WALLET_ID))
            .join(holdings).on(holdings.KIND.eq(endSnapshots.KIND)).and(holdings.HOLDING_ID.eq(endSnapshots.HOLDING_ID))
            .where(WALLETS.USER_ID.eq(userId))
            .and(endSnapshots.SNAPSHOT_DATE.eq(endDate))
            .and(endSnapshots.KIND.`in`(WalletKind.STOCKS, WalletKind.CRYPTO))
            .and(endSnapshots.CURRENT_PRICE.isNotNull)
            .fetch { record ->
                MoverCandidate(
                    kind = record.get(endSnapshots.KIND)!!.literal,
                    ticker = record.get(holdings.TICKER),
                    name = record.get(holdings.NAME),
                    currentPrice = record.get(endSnapshots.CURRENT_PRICE)!!,
                    baselinePrice = record.get(baselinePrice),
                )
            }
    }

    private fun baselinePrice(kind: Field<WalletKind?>, holdingId: Field<Long?>, monthStart: LocalDate) = DSL.coalesce(
        snapshotPrice(kind, holdingId, HOLDING_DAILY_SNAPSHOTS.SNAPSHOT_DATE.lt(monthStart), latestFirst = true),
        snapshotPrice(kind, holdingId, HOLDING_DAILY_SNAPSHOTS.SNAPSHOT_DATE.ge(monthStart), latestFirst = false),
    )

    private fun snapshotPrice(
        kind: Field<WalletKind?>,
        holdingId: Field<Long?>,
        dateCondition: Condition,
        latestFirst: Boolean,
    ) = DSL.field(
        DSL.select(HOLDING_DAILY_SNAPSHOTS.CURRENT_PRICE)
            .from(HOLDING_DAILY_SNAPSHOTS)
            .where(HOLDING_DAILY_SNAPSHOTS.KIND.eq(kind))
            .and(HOLDING_DAILY_SNAPSHOTS.HOLDING_ID.eq(holdingId))
            .and(HOLDING_DAILY_SNAPSHOTS.CURRENT_PRICE.isNotNull)
            .and(dateCondition)
            .orderBy(
                if (latestFirst) HOLDING_DAILY_SNAPSHOTS.SNAPSHOT_DATE.desc() else HOLDING_DAILY_SNAPSHOTS.SNAPSHOT_DATE.asc()
            )
            .limit(1)
    )

    private fun findCapitalTotals(userId: Long, displayCurrency: String): CapitalTotals {
        val timeline = CAPITAL_TIMELINE.`as`("timeline")
        val appliedRate = timeline.RATE.div(displayCurrencyRate(displayCurrency))
        val ownMoney = DSL.coalesce(DSL.sum(timeline.OWN_MONEY_AMOUNT.mul(appliedRate)), BigDecimal.ZERO)
        val reinvestedProfit = DSL.coalesce(DSL.sum(timeline.REINVESTED_PROFIT_AMOUNT.mul(appliedRate)), BigDecimal.ZERO)

        return dsl.select(ownMoney, reinvestedProfit)
            .from(timeline)
            .where(timeline.USER_ID.eq(userId))
            .fetchSingle { record ->
                CapitalTotals(
                    ownMoney = record.get(ownMoney)!!.withoutZeroScale(),
                    reinvestedProfit = record.get(reinvestedProfit)!!.withoutZeroScale(),
                )
            }
    }

    private fun countUnpricedHoldings(userId: Long): Int =
        dsl.fetchCount(
            HOLDINGS_VALUED,
            HOLDINGS_VALUED.USER_ID.eq(userId)
                .and(HOLDINGS_VALUED.STATUS.eq(HoldingStatus.ACTIVE))
                .and(HOLDINGS_VALUED.CURRENT_VALUE.isNull),
        )

    private fun countWalletsWithCash(userId: Long): Int =
        dsl.fetchCount(
            DSL.select(WALLET_CASH.WALLET_ID)
                .from(WALLET_CASH)
                .join(WALLETS).on(WALLETS.ID.eq(WALLET_CASH.WALLET_ID))
                .where(WALLETS.USER_ID.eq(userId))
                .and(WALLET_CASH.CASH.gt(BigDecimal.ZERO))
        )

    private fun findTotalCash(userId: Long, displayCurrency: String): BigDecimal {
        val appliedRate = DSL.coalesce(CURRENCY_RATES.RATE, BigDecimal.ONE).div(displayCurrencyRate(displayCurrency))

        return dsl.select(DSL.coalesce(DSL.sum(WALLET_CASH.CASH.mul(appliedRate)), BigDecimal.ZERO))
            .from(WALLET_CASH)
            .join(WALLETS).on(WALLETS.ID.eq(WALLET_CASH.WALLET_ID))
            .leftJoin(CURRENCY_RATES).on(CURRENCY_RATES.CURRENCY_CODE.eq(WALLETS.CURRENCY))
            .where(WALLETS.USER_ID.eq(userId))
            .fetchSingle()
            .value1()!!
            .withoutZeroScale()
    }

    private fun BigDecimal.withoutZeroScale(): BigDecimal = if (signum() == 0) BigDecimal.ZERO else this

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

    private data class CapitalTotals(val ownMoney: BigDecimal, val reinvestedProfit: BigDecimal)
}
