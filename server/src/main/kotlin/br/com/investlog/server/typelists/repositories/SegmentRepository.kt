package br.com.investlog.server.typelists.repositories

import br.com.investlog.server.jooq.finances.tables.references.CRYPTO_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.FUND_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.SEGMENTS
import br.com.investlog.server.jooq.finances.tables.references.STOCK_HOLDINGS
import br.com.investlog.server.shared.utils.pagedModelOf
import br.com.investlog.server.typelists.rest.payloads.TypeResponse
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class SegmentRepository(private val dsl: DSLContext) {

    fun findAll(pageable: Pageable): PagedModel<TypeResponse> {
        val content = dsl.select(SEGMENTS.EXTERNAL_ID, SEGMENTS.NAME, usageCountField())
            .from(SEGMENTS)
            .orderBy(SEGMENTS.NAME)
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch { record -> record.toResponse() }

        val total = dsl.fetchCount(SEGMENTS)

        return pagedModelOf(content, pageable, total.toLong())
    }

    fun findInternalId(externalId: UUID): Long? =
        dsl.select(SEGMENTS.ID).from(SEGMENTS)
            .where(SEGMENTS.EXTERNAL_ID.eq(externalId))
            .fetchOne(SEGMENTS.ID)

    fun create(name: String): TypeResponse {
        val segment = dsl.insertInto(SEGMENTS)
            .set(SEGMENTS.NAME, name)
            .returning()
            .fetchSingle()

        return dsl.select(SEGMENTS.EXTERNAL_ID, SEGMENTS.NAME, usageCountField())
            .from(SEGMENTS)
            .where(SEGMENTS.ID.eq(segment.id))
            .fetchSingle { record -> record.toResponse() }
    }

    fun update(externalId: UUID, name: String): TypeResponse? {
        val updated = dsl.update(SEGMENTS)
            .set(SEGMENTS.NAME, name)
            .where(SEGMENTS.EXTERNAL_ID.eq(externalId))
            .returning(SEGMENTS.ID)
            .fetchOne() ?: return null

        return dsl.select(SEGMENTS.EXTERNAL_ID, SEGMENTS.NAME, usageCountField())
            .from(SEGMENTS)
            .where(SEGMENTS.ID.eq(updated.id))
            .fetchSingle { record -> record.toResponse() }
    }

    fun deleteByExternalId(externalId: UUID): Int =
        dsl.deleteFrom(SEGMENTS)
            .where(SEGMENTS.EXTERNAL_ID.eq(externalId))
            .execute()

    private fun usageCountField() =
        DSL.field(
            DSL.select(
                DSL.field(DSL.selectCount().from(STOCK_HOLDINGS).where(STOCK_HOLDINGS.SEGMENT_ID.eq(SEGMENTS.ID)))
                    .plus(DSL.field(DSL.selectCount().from(CRYPTO_HOLDINGS).where(CRYPTO_HOLDINGS.SEGMENT_ID.eq(SEGMENTS.ID))))
                    .plus(DSL.field(DSL.selectCount().from(FUND_HOLDINGS).where(FUND_HOLDINGS.SEGMENT_ID.eq(SEGMENTS.ID))))
            )
        ).`as`("usage_count")

    private fun Record.toResponse() = TypeResponse(
        id = get(SEGMENTS.EXTERNAL_ID)!!,
        name = get(SEGMENTS.NAME)!!,
        usageCount = get("usage_count", Int::class.java) ?: 0,
    )
}
