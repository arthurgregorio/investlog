package br.com.investlog.server.walletsnapshots.repositories

import br.com.investlog.server.jooq.finances.enums.HoldingStatus
import br.com.investlog.server.jooq.finances.tables.references.HOLDINGS_OVERVIEW
import br.com.investlog.server.jooq.finances.tables.references.HOLDING_DAILY_SNAPSHOTS
import br.com.investlog.server.jooq.finances.tables.references.WALLETS
import br.com.investlog.server.jooq.finances.tables.references.WALLET_DAILY_SNAPSHOTS
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate

@Repository
class WalletSnapshotRepository(private val dsl: DSLContext) {

    fun findTotalsForEveryWallet(): List<WalletTotals> {
        val currentValue = DSL.coalesce(DSL.sum(HOLDINGS_OVERVIEW.CURRENT_VALUE), BigDecimal.ZERO)
        val totalInvested = DSL.coalesce(DSL.sum(HOLDINGS_OVERVIEW.COST_BASIS), BigDecimal.ZERO)

        return dsl.select(WALLETS.ID, currentValue, totalInvested)
            .from(WALLETS)
            .leftJoin(HOLDINGS_OVERVIEW).on(HOLDINGS_OVERVIEW.WALLET_ID.eq(WALLETS.ID))
            .groupBy(WALLETS.ID)
            .fetch { record ->
                WalletTotals(
                    walletId = record.get(WALLETS.ID)!!,
                    currentValue = record.get(currentValue),
                    totalInvested = record.get(totalInvested),
                )
            }
    }

    fun upsert(
        walletId: Long,
        snapshotDate: LocalDate,
        currentValue: BigDecimal,
        totalInvested: BigDecimal,
        gain: BigDecimal,
        gainPercentage: BigDecimal,
    ) {
        dsl.insertInto(WALLET_DAILY_SNAPSHOTS)
            .set(WALLET_DAILY_SNAPSHOTS.WALLET_ID, walletId)
            .set(WALLET_DAILY_SNAPSHOTS.SNAPSHOT_DATE, snapshotDate)
            .set(WALLET_DAILY_SNAPSHOTS.CURRENT_VALUE, currentValue)
            .set(WALLET_DAILY_SNAPSHOTS.TOTAL_INVESTED, totalInvested)
            .set(WALLET_DAILY_SNAPSHOTS.GAIN, gain)
            .set(WALLET_DAILY_SNAPSHOTS.GAIN_PCT, gainPercentage)
            .onConflict(WALLET_DAILY_SNAPSHOTS.WALLET_ID, WALLET_DAILY_SNAPSHOTS.SNAPSHOT_DATE)
            .doUpdate()
            .set(WALLET_DAILY_SNAPSHOTS.CURRENT_VALUE, currentValue)
            .set(WALLET_DAILY_SNAPSHOTS.TOTAL_INVESTED, totalInvested)
            .set(WALLET_DAILY_SNAPSHOTS.GAIN, gain)
            .set(WALLET_DAILY_SNAPSHOTS.GAIN_PCT, gainPercentage)
            .execute()
    }

    fun upsertHoldingSnapshots(snapshotDate: LocalDate): Int =
        dsl.insertInto(
            HOLDING_DAILY_SNAPSHOTS,
            HOLDING_DAILY_SNAPSHOTS.KIND,
            HOLDING_DAILY_SNAPSHOTS.HOLDING_ID,
            HOLDING_DAILY_SNAPSHOTS.WALLET_ID,
            HOLDING_DAILY_SNAPSHOTS.SNAPSHOT_DATE,
            HOLDING_DAILY_SNAPSHOTS.CURRENT_PRICE,
            HOLDING_DAILY_SNAPSHOTS.CURRENT_VALUE,
            HOLDING_DAILY_SNAPSHOTS.COST_BASIS,
        )
            .select(
                DSL.select(
                    HOLDINGS_OVERVIEW.KIND,
                    HOLDINGS_OVERVIEW.HOLDING_ID,
                    HOLDINGS_OVERVIEW.WALLET_ID,
                    DSL.value(snapshotDate),
                    HOLDINGS_OVERVIEW.CURRENT_PRICE,
                    HOLDINGS_OVERVIEW.CURRENT_VALUE,
                    HOLDINGS_OVERVIEW.COST_BASIS,
                )
                    .from(HOLDINGS_OVERVIEW)
                    .where(HOLDINGS_OVERVIEW.STATUS.eq(HoldingStatus.ACTIVE))
            )
            .onConflict(HOLDING_DAILY_SNAPSHOTS.KIND, HOLDING_DAILY_SNAPSHOTS.HOLDING_ID, HOLDING_DAILY_SNAPSHOTS.SNAPSHOT_DATE)
            .doUpdate()
            .set(HOLDING_DAILY_SNAPSHOTS.WALLET_ID, DSL.excluded(HOLDING_DAILY_SNAPSHOTS.WALLET_ID))
            .set(HOLDING_DAILY_SNAPSHOTS.CURRENT_PRICE, DSL.excluded(HOLDING_DAILY_SNAPSHOTS.CURRENT_PRICE))
            .set(HOLDING_DAILY_SNAPSHOTS.CURRENT_VALUE, DSL.excluded(HOLDING_DAILY_SNAPSHOTS.CURRENT_VALUE))
            .set(HOLDING_DAILY_SNAPSHOTS.COST_BASIS, DSL.excluded(HOLDING_DAILY_SNAPSHOTS.COST_BASIS))
            .execute()
}
