package br.com.investlog.server.notifications.repositories

import br.com.investlog.server.jooq.finances.tables.references.CRYPTO_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.FUND_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.RESULTS
import br.com.investlog.server.jooq.finances.tables.references.STOCK_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.WALLETS
import br.com.investlog.server.jooq.finances.tables.references.WALLET_CASH_REPLAY
import br.com.investlog.server.jooq.finances.tables.references.WALLET_DAILY_SNAPSHOTS
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate

@Repository
class PerformanceReportRepository(private val dsl: DSLContext) {

    fun findWalletSnapshots(userId: Long, startDate: LocalDate, endDate: LocalDate): List<ReportWalletSnapshot> {
        val endSnapshots = WALLET_DAILY_SNAPSHOTS.`as`("end_snapshots")
        val previousSnapshots = WALLET_DAILY_SNAPSHOTS.`as`("previous_snapshots")
        val firstSnapshots = WALLET_DAILY_SNAPSHOTS.`as`("first_snapshots")
        val startSnapshots = WALLET_DAILY_SNAPSHOTS.`as`("start_snapshots")
        val replay = WALLET_CASH_REPLAY(WALLETS.ID).`as`("replay")

        val previousSnapshotDate = DSL.field(
            DSL.select(DSL.max(previousSnapshots.SNAPSHOT_DATE))
                .from(previousSnapshots)
                .where(previousSnapshots.WALLET_ID.eq(WALLETS.ID))
                .and(previousSnapshots.SNAPSHOT_DATE.le(startDate))
        )
        val firstSnapshotDate = DSL.field(
            DSL.select(DSL.min(firstSnapshots.SNAPSHOT_DATE))
                .from(firstSnapshots)
                .where(firstSnapshots.WALLET_ID.eq(WALLETS.ID))
                .and(firstSnapshots.SNAPSHOT_DATE.gt(startDate))
                .and(firstSnapshots.SNAPSHOT_DATE.le(endDate))
        )
        val startGain = DSL.field(
            DSL.select(startSnapshots.GAIN)
                .from(startSnapshots)
                .where(startSnapshots.WALLET_ID.eq(WALLETS.ID))
                .and(startSnapshots.SNAPSHOT_DATE.eq(DSL.coalesce(previousSnapshotDate, firstSnapshotDate)))
        )
        val cash = DSL.coalesce(
            DSL.field(
                DSL.select(replay.BALANCE)
                    .from(replay)
                    .where(replay.EVENT_DATE.le(endDate))
                    .orderBy(replay.SEQUENCE.desc())
                    .limit(1)
            ),
            BigDecimal.ZERO,
        )

        return dsl.select(
            WALLETS.ID,
            WALLETS.NAME,
            WALLETS.CURRENCY,
            endSnapshots.CURRENT_VALUE,
            endSnapshots.TOTAL_INVESTED,
            endSnapshots.GAIN,
            endSnapshots.GAIN_PCT,
            previousSnapshotDate,
            firstSnapshotDate,
            startGain,
            cash,
        )
            .from(WALLETS)
            .join(endSnapshots).on(endSnapshots.WALLET_ID.eq(WALLETS.ID)).and(endSnapshots.SNAPSHOT_DATE.eq(endDate))
            .where(WALLETS.USER_ID.eq(userId))
            .orderBy(WALLETS.CREATED_AT, WALLETS.ID)
            .fetch { record ->
                val previousDate = record.get(previousSnapshotDate)

                ReportWalletSnapshot(
                    walletId = record.get(WALLETS.ID)!!,
                    name = record.get(WALLETS.NAME)!!,
                    currency = record.get(WALLETS.CURRENCY)!!,
                    currentValue = record.get(endSnapshots.CURRENT_VALUE)!!,
                    totalInvested = record.get(endSnapshots.TOTAL_INVESTED)!!,
                    gain = record.get(endSnapshots.GAIN)!!,
                    gainPercentage = record.get(endSnapshots.GAIN_PCT)!!,
                    startGain = record.get(startGain)!!,
                    firstSnapshotDate = if (previousDate == null) record.get(firstSnapshotDate) else null,
                    cash = record.get(cash)!!,
                )
            }
    }

    fun findRealizedProfits(userId: Long, startDate: LocalDate, endDate: LocalDate): List<RealizedProfit> {
        val walletId = DSL.coalesce(STOCK_HOLDINGS.WALLET_ID, CRYPTO_HOLDINGS.WALLET_ID, FUND_HOLDINGS.WALLET_ID)

        return dsl.select(walletId, RESULTS.RESULT_DATE, RESULTS.PROFIT)
            .from(RESULTS)
            .leftJoin(STOCK_HOLDINGS).on(STOCK_HOLDINGS.ID.eq(RESULTS.STOCK_HOLDING_ID))
            .leftJoin(CRYPTO_HOLDINGS).on(CRYPTO_HOLDINGS.ID.eq(RESULTS.CRYPTO_HOLDING_ID))
            .leftJoin(FUND_HOLDINGS).on(FUND_HOLDINGS.ID.eq(RESULTS.FUND_HOLDING_ID))
            .join(WALLETS).on(WALLETS.ID.eq(walletId))
            .where(WALLETS.USER_ID.eq(userId))
            .and(RESULTS.RESULT_DATE.gt(startDate))
            .and(RESULTS.RESULT_DATE.le(endDate))
            .fetch { record ->
                RealizedProfit(
                    walletId = record.get(walletId)!!,
                    resultDate = record.get(RESULTS.RESULT_DATE)!!,
                    profit = record.get(RESULTS.PROFIT) ?: BigDecimal.ZERO,
                )
            }
    }
}
