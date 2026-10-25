package br.com.investlog.server.wallets.repositories

import br.com.investlog.server.jooq.finances.tables.references.WALLETS
import br.com.investlog.server.jooq.finances.tables.references.WALLET_CASH
import br.com.investlog.server.jooq.finances.tables.references.WALLET_TOTALS
import br.com.investlog.server.shared.utils.pagedModelOf
import br.com.investlog.server.wallets.rest.payloads.WalletKind
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class WalletRepository(private val dsl: DSLContext) {

    fun findAll(userId: Long, pageable: Pageable): PagedModel<WalletResponse> {
        val content = selectWallets()
            .where(WALLETS.USER_ID.eq(userId))
            .orderBy(WALLETS.CREATED_AT.desc())
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch { record -> record.toResponse() }

        val total = dsl.fetchCount(dsl.selectFrom(WALLETS).where(WALLETS.USER_ID.eq(userId)))

        return pagedModelOf(content, pageable, total.toLong())
    }

    fun create(userId: Long, name: String, kind: WalletKind, currency: String): WalletResponse {
        val wallet = dsl.insertInto(WALLETS)
            .set(WALLETS.USER_ID, userId)
            .set(WALLETS.NAME, name)
            .set(WALLETS.KIND, kind.jooqWalletKind)
            .set(WALLETS.CURRENCY, currency)
            .returning()
            .fetchSingle()

        return selectWallets()
            .where(WALLETS.ID.eq(wallet.id))
            .fetchSingle { record -> record.toResponse() }
    }

    fun findByExternalId(userId: Long, externalId: UUID): WalletResponse? =
        selectWallets()
            .where(WALLETS.USER_ID.eq(userId))
            .and(WALLETS.EXTERNAL_ID.eq(externalId))
            .fetchOne { record -> record.toResponse() }

    fun findInternalId(userId: Long, externalId: UUID): Long? =
        dsl.select(WALLETS.ID)
            .from(WALLETS)
            .where(WALLETS.USER_ID.eq(userId))
            .and(WALLETS.EXTERNAL_ID.eq(externalId))
            .fetchOne(WALLETS.ID)

    fun update(userId: Long, externalId: UUID, name: String): WalletResponse? {
        val updated = dsl.update(WALLETS)
            .set(WALLETS.NAME, name)
            .set(WALLETS.UPDATED_AT, OffsetDateTime.now())
            .where(WALLETS.USER_ID.eq(userId))
            .and(WALLETS.EXTERNAL_ID.eq(externalId))
            .returning(WALLETS.ID)
            .fetchOne() ?: return null

        return selectWallets()
            .where(WALLETS.ID.eq(updated.id))
            .fetchSingle { record -> record.toResponse() }
    }

    fun deleteByExternalId(userId: Long, externalId: UUID): Int =
        dsl.deleteFrom(WALLETS)
            .where(WALLETS.USER_ID.eq(userId))
            .and(WALLETS.EXTERNAL_ID.eq(externalId))
            .execute()

    private fun selectWallets() =
        dsl.select(
            WALLETS.EXTERNAL_ID, WALLETS.NAME, WALLETS.KIND, WALLETS.CURRENCY, WALLETS.CREATED_AT,
            HOLDING_COUNT, TOTAL_INVESTED,
            WALLET_TOTALS.CURRENT_VALUE, WALLET_TOTALS.GAIN, WALLET_TOTALS.GAIN_PCT,
            WALLETS.CASH_RECONCILED, CASH,
        )
            .from(WALLETS)
            .leftJoin(WALLET_TOTALS).on(WALLET_TOTALS.WALLET_ID.eq(WALLETS.ID))
            .leftJoin(WALLET_CASH).on(WALLET_CASH.WALLET_ID.eq(WALLETS.ID))

    private fun Record.toResponse(): WalletResponse {
        val cash = get(CASH)!!
        val positionsValue = get(WALLET_TOTALS.CURRENT_VALUE)

        return WalletResponse(
            id = get(WALLETS.EXTERNAL_ID)!!,
            name = get(WALLETS.NAME)!!,
            kind = WalletKind.fromText(get(WALLETS.KIND)!!.literal),
            currency = get(WALLETS.CURRENCY)!!,
            holdingCount = get(HOLDING_COUNT)!!.toInt(),
            totalInvested = get(TOTAL_INVESTED)!!,
            currentValue = if (cash.signum() == 0) positionsValue else (positionsValue ?: BigDecimal.ZERO) + cash,
            gain = get(WALLET_TOTALS.GAIN),
            gainPct = get(WALLET_TOTALS.GAIN_PCT),
            cash = cash,
            cashReconciled = get(WALLETS.CASH_RECONCILED)!!,
            createdAt = get(WALLETS.CREATED_AT)!!,
        )
    }

    private companion object {
        val HOLDING_COUNT = DSL.coalesce(WALLET_TOTALS.HOLDING_COUNT, 0L).`as`("holding_count")
        val TOTAL_INVESTED = DSL.coalesce(WALLET_TOTALS.TOTAL_INVESTED, BigDecimal.ZERO).`as`("total_invested")
        val CASH = DSL.coalesce(WALLET_CASH.CASH, BigDecimal.ZERO).`as`("cash")
    }
}
