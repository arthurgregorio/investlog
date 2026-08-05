package br.com.investlog.server.wallets.repositories

import br.com.investlog.server.jooq.finances.tables.references.HOLDINGS_OVERVIEW
import br.com.investlog.server.jooq.finances.tables.references.WALLETS
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
import java.math.RoundingMode
import java.time.OffsetDateTime
import java.util.UUID
import br.com.investlog.server.jooq.finances.enums.WalletKind as JooqWalletKind

@Repository
class WalletRepository(private val dsl: DSLContext) {

    fun findAll(userId: Long, pageable: Pageable): PagedModel<WalletResponse> {
        val content = dsl.select(
            WALLETS.EXTERNAL_ID, WALLETS.NAME, WALLETS.KIND, WALLETS.CURRENCY, WALLETS.CREATED_AT,
            holdingCountField(), totalInvestedField(), currentValueField(),
        )
            .from(WALLETS)
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
            .set(WALLETS.KIND, JooqWalletKind.valueOf(kind.name))
            .set(WALLETS.CURRENCY, currency)
            .returning()
            .fetchSingle()

        return dsl.select(
            WALLETS.EXTERNAL_ID, WALLETS.NAME, WALLETS.KIND, WALLETS.CURRENCY, WALLETS.CREATED_AT,
            holdingCountField(), totalInvestedField(), currentValueField(),
        )
            .from(WALLETS)
            .where(WALLETS.ID.eq(wallet.id))
            .fetchSingle { record -> record.toResponse() }
    }

    fun findByExternalId(userId: Long, externalId: UUID): WalletResponse? =
        dsl.select(
            WALLETS.EXTERNAL_ID, WALLETS.NAME, WALLETS.KIND, WALLETS.CURRENCY, WALLETS.CREATED_AT,
            holdingCountField(), totalInvestedField(), currentValueField(),
        )
            .from(WALLETS)
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

        return dsl.select(
            WALLETS.EXTERNAL_ID, WALLETS.NAME, WALLETS.KIND, WALLETS.CURRENCY, WALLETS.CREATED_AT,
            holdingCountField(), totalInvestedField(), currentValueField(),
        )
            .from(WALLETS)
            .where(WALLETS.ID.eq(updated.id))
            .fetchSingle { record -> record.toResponse() }
    }

    fun deleteByExternalId(userId: Long, externalId: UUID): Int =
        dsl.deleteFrom(WALLETS)
            .where(WALLETS.USER_ID.eq(userId))
            .and(WALLETS.EXTERNAL_ID.eq(externalId))
            .execute()

    private fun holdingCountField() =
        DSL.field(
            DSL.selectCount()
                .from(HOLDINGS_OVERVIEW)
                .where(HOLDINGS_OVERVIEW.WALLET_ID.eq(WALLETS.ID))
        ).`as`("holding_count")

    private fun totalInvestedField() =
        DSL.field(
            DSL.select(DSL.coalesce(DSL.sum(HOLDINGS_OVERVIEW.COST_BASIS), BigDecimal.ZERO))
                .from(HOLDINGS_OVERVIEW)
                .where(HOLDINGS_OVERVIEW.WALLET_ID.eq(WALLETS.ID))
        ).`as`("total_invested")

    private fun currentValueField() =
        DSL.field(
            DSL.select(DSL.sum(HOLDINGS_OVERVIEW.CURRENT_VALUE))
                .from(HOLDINGS_OVERVIEW)
                .where(HOLDINGS_OVERVIEW.WALLET_ID.eq(WALLETS.ID))
        ).`as`("current_value")

    private fun Record.toResponse(): WalletResponse {
        val totalInvested = get("total_invested", BigDecimal::class.java) ?: BigDecimal.ZERO
        val currentValue = get("current_value", BigDecimal::class.java)
        val gain = currentValue?.let { it - totalInvested }
        val gainPct = if (gain != null && totalInvested.signum() != 0) {
            gain.divide(totalInvested, 10, RoundingMode.HALF_UP).multiply(BigDecimal("100"))
        } else null

        return WalletResponse(
            id = get(WALLETS.EXTERNAL_ID)!!,
            name = get(WALLETS.NAME)!!,
            kind = WalletKind.fromText(get(WALLETS.KIND)!!.literal),
            currency = get(WALLETS.CURRENCY)!!,
            holdingCount = get("holding_count", Int::class.java) ?: 0,
            totalInvested = totalInvested,
            currentValue = currentValue,
            gain = gain,
            gainPct = gainPct,
            createdAt = get(WALLETS.CREATED_AT)!!,
        )
    }
}
