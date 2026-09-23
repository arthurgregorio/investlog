package br.com.investlog.server.walletdetail.repositories

import br.com.investlog.server.jooq.finances.tables.references.CRYPTO_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.CRYPTO_LOTS
import br.com.investlog.server.jooq.finances.tables.references.FUND_CONTRIBUTIONS
import br.com.investlog.server.jooq.finances.tables.references.FUND_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.HOLDINGS_OVERVIEW
import br.com.investlog.server.jooq.finances.tables.references.STOCK_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.STOCK_LOTS
import br.com.investlog.server.jooq.finances.tables.references.WALLET_DAILY_SNAPSHOTS
import br.com.investlog.server.walletdetail.rest.payloads.WalletSnapshotPointResponse
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal

@Repository
class WalletDetailRepository(private val dsl: DSLContext) {

    fun findHoldings(walletId: Long): List<WalletHolding> =
        dsl.select(
            HOLDINGS_OVERVIEW.EXTERNAL_ID,
            HOLDINGS_OVERVIEW.NAME,
            HOLDINGS_OVERVIEW.TICKER,
            HOLDINGS_OVERVIEW.KIND,
            HOLDINGS_OVERVIEW.COST_BASIS,
            HOLDINGS_OVERVIEW.CURRENT_VALUE,
        )
            .from(HOLDINGS_OVERVIEW)
            .where(HOLDINGS_OVERVIEW.WALLET_ID.eq(walletId))
            .fetch { record ->
                WalletHolding(
                    id = record.get(HOLDINGS_OVERVIEW.EXTERNAL_ID)!!,
                    name = record.get(HOLDINGS_OVERVIEW.NAME)!!,
                    ticker = record.get(HOLDINGS_OVERVIEW.TICKER),
                    kind = record.get(HOLDINGS_OVERVIEW.KIND)!!.literal,
                    costBasis = record.get(HOLDINGS_OVERVIEW.COST_BASIS) ?: BigDecimal.ZERO,
                    currentValue = record.get(HOLDINGS_OVERVIEW.CURRENT_VALUE),
                )
            }

    fun findSnapshots(walletId: Long): List<WalletSnapshotPointResponse> =
        dsl.select(
            WALLET_DAILY_SNAPSHOTS.SNAPSHOT_DATE,
            WALLET_DAILY_SNAPSHOTS.CURRENT_VALUE,
            WALLET_DAILY_SNAPSHOTS.TOTAL_INVESTED,
            WALLET_DAILY_SNAPSHOTS.GAIN,
            WALLET_DAILY_SNAPSHOTS.GAIN_PCT,
        )
            .from(WALLET_DAILY_SNAPSHOTS)
            .where(WALLET_DAILY_SNAPSHOTS.WALLET_ID.eq(walletId))
            .orderBy(WALLET_DAILY_SNAPSHOTS.SNAPSHOT_DATE.asc())
            .fetch { record ->
                WalletSnapshotPointResponse(
                    snapshotDate = record.get(WALLET_DAILY_SNAPSHOTS.SNAPSHOT_DATE)!!,
                    currentValue = record.get(WALLET_DAILY_SNAPSHOTS.CURRENT_VALUE)!!,
                    totalInvested = record.get(WALLET_DAILY_SNAPSHOTS.TOTAL_INVESTED)!!,
                    gain = record.get(WALLET_DAILY_SNAPSHOTS.GAIN)!!,
                    gainPct = record.get(WALLET_DAILY_SNAPSHOTS.GAIN_PCT)!!,
                )
            }

    fun findTransactions(walletId: Long): List<WalletTransaction> {
        val stockTransactions = dsl.select(
            STOCK_LOTS.LOT_DATE.`as`("transaction_date"),
            STOCK_HOLDINGS.NAME.`as`("investment_name"),
            STOCK_LOTS.QUANTITY.mul(STOCK_LOTS.PRICE).`as`("amount"),
        )
            .from(STOCK_LOTS)
            .join(STOCK_HOLDINGS).on(STOCK_HOLDINGS.ID.eq(STOCK_LOTS.STOCK_HOLDING_ID))
            .where(STOCK_HOLDINGS.WALLET_ID.eq(walletId))

        val cryptoTransactions = dsl.select(
            CRYPTO_LOTS.LOT_DATE.`as`("transaction_date"),
            CRYPTO_HOLDINGS.NAME.`as`("investment_name"),
            CRYPTO_LOTS.QUANTITY.mul(CRYPTO_LOTS.PRICE).`as`("amount"),
        )
            .from(CRYPTO_LOTS)
            .join(CRYPTO_HOLDINGS).on(CRYPTO_HOLDINGS.ID.eq(CRYPTO_LOTS.CRYPTO_HOLDING_ID))
            .where(CRYPTO_HOLDINGS.WALLET_ID.eq(walletId))

        val fundTransactions = dsl.select(
            FUND_CONTRIBUTIONS.CONTRIBUTION_DATE.`as`("transaction_date"),
            FUND_HOLDINGS.NAME.`as`("investment_name"),
            FUND_CONTRIBUTIONS.AMOUNT.`as`("amount"),
        )
            .from(FUND_CONTRIBUTIONS)
            .join(FUND_HOLDINGS).on(FUND_HOLDINGS.ID.eq(FUND_CONTRIBUTIONS.FUND_HOLDING_ID))
            .where(FUND_HOLDINGS.WALLET_ID.eq(walletId))

        return stockTransactions
            .unionAll(cryptoTransactions)
            .unionAll(fundTransactions)
            .orderBy(DSL.field("transaction_date").desc())
            .fetch { record ->
                WalletTransaction(
                    transactionDate = record.get("transaction_date", java.time.LocalDate::class.java)!!,
                    investmentName = record.get("investment_name", String::class.java)!!,
                    amount = record.get("amount", BigDecimal::class.java) ?: BigDecimal.ZERO,
                )
            }
    }
}
