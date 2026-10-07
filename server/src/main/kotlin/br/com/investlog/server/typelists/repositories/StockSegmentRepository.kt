package br.com.investlog.server.typelists.repositories

import br.com.investlog.server.jooq.finances.tables.references.STOCK_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.STOCK_SEGMENTS
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
class StockSegmentRepository(private val dsl: DSLContext) {

    fun findAll(pageable: Pageable): PagedModel<TypeResponse> {
        val content = dsl.select(STOCK_SEGMENTS.EXTERNAL_ID, STOCK_SEGMENTS.NAME, usageCountField())
            .from(STOCK_SEGMENTS)
            .orderBy(STOCK_SEGMENTS.NAME)
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch { record -> record.toResponse() }

        val total = dsl.fetchCount(STOCK_SEGMENTS)

        return pagedModelOf(content, pageable, total.toLong())
    }

    fun create(name: String): TypeResponse {
        val stockSegment = dsl.insertInto(STOCK_SEGMENTS)
            .set(STOCK_SEGMENTS.NAME, name)
            .returning()
            .fetchSingle()

        return dsl.select(STOCK_SEGMENTS.EXTERNAL_ID, STOCK_SEGMENTS.NAME, usageCountField())
            .from(STOCK_SEGMENTS)
            .where(STOCK_SEGMENTS.ID.eq(stockSegment.id))
            .fetchSingle { record -> record.toResponse() }
    }

    fun update(externalId: UUID, name: String): TypeResponse? {
        val updated = dsl.update(STOCK_SEGMENTS)
            .set(STOCK_SEGMENTS.NAME, name)
            .where(STOCK_SEGMENTS.EXTERNAL_ID.eq(externalId))
            .returning(STOCK_SEGMENTS.ID)
            .fetchOne() ?: return null

        return dsl.select(STOCK_SEGMENTS.EXTERNAL_ID, STOCK_SEGMENTS.NAME, usageCountField())
            .from(STOCK_SEGMENTS)
            .where(STOCK_SEGMENTS.ID.eq(updated.id))
            .fetchSingle { record -> record.toResponse() }
    }

    fun deleteByExternalId(externalId: UUID): Int =
        dsl.deleteFrom(STOCK_SEGMENTS)
            .where(STOCK_SEGMENTS.EXTERNAL_ID.eq(externalId))
            .execute()

    private fun usageCountField() =
        DSL.field(
            DSL.selectCount()
                .from(STOCK_HOLDINGS)
                .where(STOCK_HOLDINGS.STOCK_SEGMENT_ID.eq(STOCK_SEGMENTS.ID))
        ).`as`("usage_count")

    private fun Record.toResponse() = TypeResponse(
        id = get(STOCK_SEGMENTS.EXTERNAL_ID)!!,
        name = get(STOCK_SEGMENTS.NAME)!!,
        usageCount = get("usage_count", Int::class.java) ?: 0,
    )
}
