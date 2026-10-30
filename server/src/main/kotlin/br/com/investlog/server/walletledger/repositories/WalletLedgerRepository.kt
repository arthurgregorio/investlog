package br.com.investlog.server.walletledger.repositories

import br.com.investlog.server.jooq.finances.tables.references.WALLETS
import br.com.investlog.server.jooq.finances.tables.references.WALLET_LEDGER
import br.com.investlog.server.walletledger.rest.payloads.WalletLedgerEventType
import org.jooq.DSLContext
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class WalletLedgerRepository(private val dsl: DSLContext) {

    fun findWalletId(userId: Long, externalId: UUID): Long? =
        dsl.select(WALLETS.ID)
            .from(WALLETS)
            .where(WALLETS.USER_ID.eq(userId))
            .and(WALLETS.EXTERNAL_ID.eq(externalId))
            .fetchOne(WALLETS.ID)

    fun findByWallet(walletId: Long, pageable: Pageable): WalletLedgerPage {
        val walletLedger = WALLET_LEDGER.`as`("wallet_ledger")
        val belongsToWallet = walletLedger.WALLET_ID.eq(walletId)

        val rows = dsl.selectFrom(walletLedger)
            .where(belongsToWallet)
            .orderBy(walletLedger.EVENT_DATE.desc(), walletLedger.CREATED_AT.desc(), walletLedger.EVENT_ID.desc())
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch { record ->
                WalletLedgerRow(
                    id = record.eventId!!,
                    date = record.eventDate!!,
                    createdAt = record.createdAt!!,
                    type = WalletLedgerEventType.valueOf(record.eventType!!),
                    kind = record.kind!!.literal,
                    holdingId = record.holdingExternalId,
                    holdingName = record.holdingName,
                    ticker = record.ticker,
                    quantity = record.quantity,
                    amount = record.amount,
                    profit = record.profit,
                    counterpartName = record.counterpartName,
                    originalAmount = record.originalAmount,
                    originalCurrency = record.originalCurrency,
                    rate = record.rate,
                )
            }

        val total = dsl.fetchCount(dsl.selectFrom(walletLedger).where(belongsToWallet))

        return WalletLedgerPage(rows, total.toLong())
    }
}
