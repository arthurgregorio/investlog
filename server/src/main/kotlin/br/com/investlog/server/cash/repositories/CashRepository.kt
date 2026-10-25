package br.com.investlog.server.cash.repositories

import br.com.investlog.server.jooq.finances.tables.references.CASH_TRANSFERS
import br.com.investlog.server.jooq.finances.tables.references.CASH_WITHDRAWALS
import br.com.investlog.server.jooq.finances.tables.references.CRYPTO_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.CRYPTO_LOTS
import br.com.investlog.server.jooq.finances.tables.references.FUND_CONTRIBUTIONS
import br.com.investlog.server.jooq.finances.tables.references.FUND_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.RESULTS
import br.com.investlog.server.jooq.finances.tables.references.STOCK_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.STOCK_LOTS
import br.com.investlog.server.jooq.finances.tables.references.WALLETS
import br.com.investlog.server.jooq.finances.tables.references.WALLET_CASH_REPLAY
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class CashRepository(private val dsl: DSLContext) {

    fun findWallet(userId: Long, externalId: UUID): CashWallet? =
        selectWallets()
            .where(WALLETS.USER_ID.eq(userId))
            .and(WALLETS.EXTERNAL_ID.eq(externalId))
            .fetchOne { record -> record.toCashWallet() }

    fun findWalletById(walletId: Long): CashWallet =
        selectWallets()
            .where(WALLETS.ID.eq(walletId))
            .fetchSingle { record -> record.toCashWallet() }

    fun findWallets(userId: Long): List<CashWallet> =
        selectWallets()
            .where(WALLETS.USER_ID.eq(userId))
            .orderBy(WALLETS.CREATED_AT, WALLETS.ID)
            .fetch { record -> record.toCashWallet() }

    fun replay(walletId: Long): List<CashReplayEvent> {
        val replay = WALLET_CASH_REPLAY(walletId).`as`("replay")

        return dsl.selectFrom(replay)
            .orderBy(replay.SEQUENCE)
            .fetch { record ->
                CashReplayEvent(
                    sequence = record.sequence!!,
                    eventType = record.eventType!!,
                    eventId = record.eventId!!,
                    eventDate = record.eventDate!!,
                    createdAt = record.createdAt!!,
                    amount = record.amount!!,
                    cashUsed = record.cashUsed,
                    newMoney = record.newMoney,
                    balance = record.balance!!,
                )
            }
    }

    fun insertTransfer(
        originWalletId: Long,
        destinationWalletId: Long,
        transferDate: LocalDate,
        amount: BigDecimal,
        rate: BigDecimal,
    ): CashTransferRow =
        dsl.insertInto(CASH_TRANSFERS)
            .set(CASH_TRANSFERS.ORIGIN_WALLET_ID, originWalletId)
            .set(CASH_TRANSFERS.DESTINATION_WALLET_ID, destinationWalletId)
            .set(CASH_TRANSFERS.TRANSFER_DATE, transferDate)
            .set(CASH_TRANSFERS.AMOUNT, amount)
            .set(CASH_TRANSFERS.RATE, rate)
            .returning()
            .fetchSingle { record ->
                CashTransferRow(
                    id = record.id!!,
                    externalId = record.externalId!!,
                    originWalletId = record.originWalletId!!,
                    destinationWalletId = record.destinationWalletId!!,
                    transferDate = record.transferDate!!,
                    amount = record.amount!!,
                    rate = record.rate!!,
                    destinationAmount = record.destinationAmount!!,
                )
            }

    fun findTransferTouching(walletId: Long, externalId: UUID): CashTransferRow? =
        dsl.selectFrom(CASH_TRANSFERS)
            .where(CASH_TRANSFERS.EXTERNAL_ID.eq(externalId))
            .and(CASH_TRANSFERS.ORIGIN_WALLET_ID.eq(walletId).or(CASH_TRANSFERS.DESTINATION_WALLET_ID.eq(walletId)))
            .fetchOne { record ->
                CashTransferRow(
                    id = record.id!!,
                    externalId = record.externalId!!,
                    originWalletId = record.originWalletId!!,
                    destinationWalletId = record.destinationWalletId!!,
                    transferDate = record.transferDate!!,
                    amount = record.amount!!,
                    rate = record.rate!!,
                    destinationAmount = record.destinationAmount!!,
                )
            }

    fun deleteTransfer(transferId: Long) {
        dsl.deleteFrom(CASH_TRANSFERS)
            .where(CASH_TRANSFERS.ID.eq(transferId))
            .execute()
    }

    fun insertWithdrawal(walletId: Long, withdrawalDate: LocalDate, amount: BigDecimal, bankName: String?): CashWithdrawalRow =
        dsl.insertInto(CASH_WITHDRAWALS)
            .set(CASH_WITHDRAWALS.WALLET_ID, walletId)
            .set(CASH_WITHDRAWALS.WITHDRAWAL_DATE, withdrawalDate)
            .set(CASH_WITHDRAWALS.AMOUNT, amount)
            .set(CASH_WITHDRAWALS.BANK_NAME, bankName)
            .returning()
            .fetchSingle { record ->
                CashWithdrawalRow(
                    id = record.id!!,
                    externalId = record.externalId!!,
                    withdrawalDate = record.withdrawalDate!!,
                    amount = record.amount!!,
                    bankName = record.bankName,
                )
            }

    fun findWithdrawalId(walletId: Long, externalId: UUID): Long? =
        dsl.select(CASH_WITHDRAWALS.ID)
            .from(CASH_WITHDRAWALS)
            .where(CASH_WITHDRAWALS.WALLET_ID.eq(walletId))
            .and(CASH_WITHDRAWALS.EXTERNAL_ID.eq(externalId))
            .fetchOne(CASH_WITHDRAWALS.ID)

    fun deleteWithdrawal(withdrawalId: Long) {
        dsl.deleteFrom(CASH_WITHDRAWALS)
            .where(CASH_WITHDRAWALS.ID.eq(withdrawalId))
            .execute()
    }

    fun markReconciled(walletId: Long) {
        dsl.update(WALLETS)
            .set(WALLETS.CASH_RECONCILED, true)
            .set(WALLETS.UPDATED_AT, OffsetDateTime.now())
            .where(WALLETS.ID.eq(walletId))
            .execute()
    }

    fun findSources(resultIds: Collection<UUID>): List<CashSource> {
        if (resultIds.isEmpty()) return emptyList()

        return dsl.select(
            RESULTS.EXTERNAL_ID,
            RESULTS.RESULT_DATE,
            RESULTS.NET_AMOUNT,
            DSL.coalesce(STOCK_HOLDINGS.NAME, CRYPTO_HOLDINGS.NAME, FUND_HOLDINGS.NAME).`as`("holding_name"),
            DSL.coalesce(STOCK_HOLDINGS.TICKER, CRYPTO_HOLDINGS.TICKER).`as`("ticker"),
        )
            .from(RESULTS)
            .leftJoin(STOCK_HOLDINGS).on(STOCK_HOLDINGS.ID.eq(RESULTS.STOCK_HOLDING_ID))
            .leftJoin(CRYPTO_HOLDINGS).on(CRYPTO_HOLDINGS.ID.eq(RESULTS.CRYPTO_HOLDING_ID))
            .leftJoin(FUND_HOLDINGS).on(FUND_HOLDINGS.ID.eq(RESULTS.FUND_HOLDING_ID))
            .where(RESULTS.EXTERNAL_ID.`in`(resultIds))
            .orderBy(RESULTS.RESULT_DATE, RESULTS.ID)
            .fetch { record ->
                CashSource(
                    resultId = record.get(RESULTS.EXTERNAL_ID)!!,
                    resultDate = record.get(RESULTS.RESULT_DATE)!!,
                    holdingName = record.get("holding_name", String::class.java)!!,
                    ticker = record.get("ticker", String::class.java),
                    amount = record.get(RESULTS.NET_AMOUNT)!!,
                )
            }
    }

    fun findPurchases(eventIds: Collection<UUID>): Map<UUID, FundedPurchase> {
        if (eventIds.isEmpty()) return emptyMap()

        val stockPurchases = dsl.select(STOCK_LOTS.EXTERNAL_ID, STOCK_LOTS.LOT_DATE, STOCK_HOLDINGS.NAME, STOCK_HOLDINGS.TICKER)
            .from(STOCK_LOTS)
            .join(STOCK_HOLDINGS).on(STOCK_HOLDINGS.ID.eq(STOCK_LOTS.STOCK_HOLDING_ID))
            .where(STOCK_LOTS.EXTERNAL_ID.`in`(eventIds))
        val cryptoPurchases = dsl.select(CRYPTO_LOTS.EXTERNAL_ID, CRYPTO_LOTS.LOT_DATE, CRYPTO_HOLDINGS.NAME, CRYPTO_HOLDINGS.TICKER)
            .from(CRYPTO_LOTS)
            .join(CRYPTO_HOLDINGS).on(CRYPTO_HOLDINGS.ID.eq(CRYPTO_LOTS.CRYPTO_HOLDING_ID))
            .where(CRYPTO_LOTS.EXTERNAL_ID.`in`(eventIds))
        val fundContributions = dsl.select(
            FUND_CONTRIBUTIONS.EXTERNAL_ID,
            FUND_CONTRIBUTIONS.CONTRIBUTION_DATE,
            FUND_HOLDINGS.NAME,
            DSL.inline(null, String::class.java),
        )
            .from(FUND_CONTRIBUTIONS)
            .join(FUND_HOLDINGS).on(FUND_HOLDINGS.ID.eq(FUND_CONTRIBUTIONS.FUND_HOLDING_ID))
            .where(FUND_CONTRIBUTIONS.EXTERNAL_ID.`in`(eventIds))

        return stockPurchases.unionAll(cryptoPurchases).unionAll(fundContributions)
            .fetch { record ->
                FundedPurchase(
                    eventId = record.get(0, UUID::class.java)!!,
                    purchaseDate = record.get(1, LocalDate::class.java)!!,
                    holdingName = record.get(2, String::class.java)!!,
                    ticker = record.get(3, String::class.java),
                )
            }
            .associateBy { purchase -> purchase.eventId }
    }

    private fun selectWallets() =
        dsl.select(WALLETS.ID, WALLETS.EXTERNAL_ID, WALLETS.NAME, WALLETS.KIND, WALLETS.CURRENCY, WALLETS.CASH_RECONCILED)
            .from(WALLETS)

    private fun Record.toCashWallet() = CashWallet(
        id = get(WALLETS.ID)!!,
        externalId = get(WALLETS.EXTERNAL_ID)!!,
        name = get(WALLETS.NAME)!!,
        kind = get(WALLETS.KIND)!!,
        currency = get(WALLETS.CURRENCY)!!,
        cashReconciled = get(WALLETS.CASH_RECONCILED)!!,
    )
}
