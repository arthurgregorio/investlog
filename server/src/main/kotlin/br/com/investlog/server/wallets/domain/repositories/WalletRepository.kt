package br.com.investlog.server.wallets.domain.repositories

import br.com.investlog.server.jooq.finances.tables.records.WalletsRecord
import br.com.investlog.server.jooq.finances.tables.references.WALLETS
import br.com.investlog.server.shared.persistence.pagedModelOf
import br.com.investlog.server.wallets.rest.payloads.WalletKind
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import org.jooq.DSLContext
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID
import br.com.investlog.server.jooq.finances.enums.WalletKind as JooqWalletKind

@Repository
class WalletRepository(private val dsl: DSLContext) {

    fun findAll(userId: Long, pageable: Pageable): PagedModel<WalletResponse> {
        val content = dsl.selectFrom(WALLETS)
            .where(WALLETS.USER_ID.eq(userId))
            .orderBy(WALLETS.CREATED_AT.desc())
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch()
            .map { it.toResponse() }

        val total = dsl.fetchCount(dsl.selectFrom(WALLETS).where(WALLETS.USER_ID.eq(userId)))

        return pagedModelOf(content, pageable, total.toLong())
    }

    fun create(userId: Long, name: String, kind: WalletKind, currency: String): WalletResponse =
        dsl.insertInto(WALLETS)
            .set(WALLETS.USER_ID, userId)
            .set(WALLETS.NAME, name)
            .set(WALLETS.KIND, JooqWalletKind.valueOf(kind.name))
            .set(WALLETS.CURRENCY, currency)
            .returning()
            .fetchSingle()
            .toResponse()

    fun findByExternalId(userId: Long, externalId: UUID): WalletResponse? =
        dsl.selectFrom(WALLETS)
            .where(WALLETS.USER_ID.eq(userId))
            .and(WALLETS.EXTERNAL_ID.eq(externalId))
            .fetchOne()
            ?.toResponse()

    fun findInternalId(userId: Long, externalId: UUID): Long? =
        dsl.select(WALLETS.ID)
            .from(WALLETS)
            .where(WALLETS.USER_ID.eq(userId))
            .and(WALLETS.EXTERNAL_ID.eq(externalId))
            .fetchOne(WALLETS.ID)

    fun update(userId: Long, externalId: UUID, name: String): WalletResponse? =
        dsl.update(WALLETS)
            .set(WALLETS.NAME, name)
            .set(WALLETS.UPDATED_AT, OffsetDateTime.now())
            .where(WALLETS.USER_ID.eq(userId))
            .and(WALLETS.EXTERNAL_ID.eq(externalId))
            .returning()
            .fetchOne()
            ?.toResponse()

    fun deleteByExternalId(userId: Long, externalId: UUID): Int =
        dsl.deleteFrom(WALLETS)
            .where(WALLETS.USER_ID.eq(userId))
            .and(WALLETS.EXTERNAL_ID.eq(externalId))
            .execute()

    private fun WalletsRecord.toResponse() = WalletResponse(
        id = externalId!!,
        name = name!!,
        kind = kind!!.literal,
        currency = currency!!,
        createdAt = createdAt!!,
    )
}
