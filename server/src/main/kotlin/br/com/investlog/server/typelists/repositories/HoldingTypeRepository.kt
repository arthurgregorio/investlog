package br.com.investlog.server.typelists.repositories

import br.com.investlog.server.jooq.finances.tables.references.CRYPTO_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.FUND_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.HOLDING_TYPES
import br.com.investlog.server.jooq.finances.tables.references.STOCK_HOLDINGS
import br.com.investlog.server.shared.utils.pagedModelOf
import br.com.investlog.server.typelists.rest.payloads.HoldingTypeResponse
import br.com.investlog.server.wallets.rest.payloads.WalletKind
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Repository
import java.util.UUID
import br.com.investlog.server.jooq.finances.enums.WalletKind as JooqWalletKind

@Repository
class HoldingTypeRepository(private val dsl: DSLContext) {

    fun findAll(kind: JooqWalletKind?, pageable: Pageable): PagedModel<HoldingTypeResponse> {
        val kindCondition = kind?.let { HOLDING_TYPES.KINDS.contains(arrayOf<JooqWalletKind?>(it)) } ?: DSL.noCondition()

        val content = dsl.select(HOLDING_TYPES.EXTERNAL_ID, HOLDING_TYPES.NAME, HOLDING_TYPES.KINDS, usageCountField())
            .from(HOLDING_TYPES)
            .where(kindCondition)
            .orderBy(HOLDING_TYPES.NAME)
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch { record -> record.toResponse() }

        val total = dsl.fetchCount(HOLDING_TYPES, kindCondition)

        return pagedModelOf(content, pageable, total.toLong())
    }

    fun findReference(externalId: UUID): HoldingTypeReference? =
        dsl.select(HOLDING_TYPES.ID, HOLDING_TYPES.KINDS)
            .from(HOLDING_TYPES)
            .where(HOLDING_TYPES.EXTERNAL_ID.eq(externalId))
            .fetchOne { record ->
                HoldingTypeReference(
                    id = record.get(HOLDING_TYPES.ID)!!,
                    kinds = record.get(HOLDING_TYPES.KINDS)!!.filterNotNull().toSet(),
                )
            }

    fun create(name: String, kinds: Set<WalletKind>): HoldingTypeResponse {
        val holdingType = dsl.insertInto(HOLDING_TYPES)
            .set(HOLDING_TYPES.NAME, name)
            .set(HOLDING_TYPES.KINDS, kinds.toJooqArray())
            .returning(HOLDING_TYPES.ID)
            .fetchSingle()

        return findById(holdingType.id!!)
    }

    fun update(internalId: Long, name: String, kinds: Set<WalletKind>): HoldingTypeResponse {
        dsl.update(HOLDING_TYPES)
            .set(HOLDING_TYPES.NAME, name)
            .set(HOLDING_TYPES.KINDS, kinds.toJooqArray())
            .where(HOLDING_TYPES.ID.eq(internalId))
            .execute()

        return findById(internalId)
    }

    fun countUsage(internalId: Long, kind: JooqWalletKind): Int {
        val (table, typeColumn) = when (kind) {
            JooqWalletKind.STOCKS -> STOCK_HOLDINGS to STOCK_HOLDINGS.HOLDING_TYPE_ID
            JooqWalletKind.CRYPTO -> CRYPTO_HOLDINGS to CRYPTO_HOLDINGS.HOLDING_TYPE_ID
            JooqWalletKind.FUNDS -> FUND_HOLDINGS to FUND_HOLDINGS.HOLDING_TYPE_ID
        }
        return dsl.fetchCount(table, typeColumn.eq(internalId))
    }

    fun deleteByExternalId(externalId: UUID): Int =
        dsl.deleteFrom(HOLDING_TYPES)
            .where(HOLDING_TYPES.EXTERNAL_ID.eq(externalId))
            .execute()

    private fun findById(internalId: Long): HoldingTypeResponse =
        dsl.select(HOLDING_TYPES.EXTERNAL_ID, HOLDING_TYPES.NAME, HOLDING_TYPES.KINDS, usageCountField())
            .from(HOLDING_TYPES)
            .where(HOLDING_TYPES.ID.eq(internalId))
            .fetchSingle { record -> record.toResponse() }

    private fun usageCountField() =
        DSL.field(
            DSL.select(
                DSL.field(DSL.selectCount().from(STOCK_HOLDINGS).where(STOCK_HOLDINGS.HOLDING_TYPE_ID.eq(HOLDING_TYPES.ID)))
                    .plus(DSL.field(DSL.selectCount().from(CRYPTO_HOLDINGS).where(CRYPTO_HOLDINGS.HOLDING_TYPE_ID.eq(HOLDING_TYPES.ID))))
                    .plus(DSL.field(DSL.selectCount().from(FUND_HOLDINGS).where(FUND_HOLDINGS.HOLDING_TYPE_ID.eq(HOLDING_TYPES.ID))))
            )
        ).`as`("usage_count")

    private fun Set<WalletKind>.toJooqArray(): Array<JooqWalletKind?> =
        map { kind -> kind.jooqWalletKind }.toTypedArray()

    private fun Record.toResponse(): HoldingTypeResponse {
        val kinds = get(HOLDING_TYPES.KINDS)!!.toSet()
        return HoldingTypeResponse(
            id = get(HOLDING_TYPES.EXTERNAL_ID)!!,
            name = get(HOLDING_TYPES.NAME)!!,
            kinds = WalletKind.entries.filter { kind -> kind.jooqWalletKind in kinds },
            usageCount = get("usage_count", Int::class.java) ?: 0,
        )
    }
}
