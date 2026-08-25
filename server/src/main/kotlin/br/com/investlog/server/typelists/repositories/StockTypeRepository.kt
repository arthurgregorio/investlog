package br.com.investlog.server.typelists.repositories

import br.com.investlog.server.jooq.finances.tables.references.STOCK_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.STOCK_TYPES
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
class StockTypeRepository(private val dsl: DSLContext) {

    fun findAll(pageable: Pageable): PagedModel<TypeResponse> {
        val content = dsl.select(STOCK_TYPES.EXTERNAL_ID, STOCK_TYPES.NAME, usageCountField())
            .from(STOCK_TYPES)
            .orderBy(STOCK_TYPES.NAME)
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch { record -> record.toResponse() }

        val total = dsl.fetchCount(STOCK_TYPES)

        return pagedModelOf(content, pageable, total.toLong())
    }

    fun create(name: String): TypeResponse {
        val stockType = dsl.insertInto(STOCK_TYPES)
            .set(STOCK_TYPES.NAME, name)
            .returning()
            .fetchSingle()

        return dsl.select(STOCK_TYPES.EXTERNAL_ID, STOCK_TYPES.NAME, usageCountField())
            .from(STOCK_TYPES)
            .where(STOCK_TYPES.ID.eq(stockType.id))
            .fetchSingle { record -> record.toResponse() }
    }

    fun update(externalId: UUID, name: String): TypeResponse? {
        val updated = dsl.update(STOCK_TYPES)
            .set(STOCK_TYPES.NAME, name)
            .where(STOCK_TYPES.EXTERNAL_ID.eq(externalId))
            .returning(STOCK_TYPES.ID)
            .fetchOne() ?: return null

        return dsl.select(STOCK_TYPES.EXTERNAL_ID, STOCK_TYPES.NAME, usageCountField())
            .from(STOCK_TYPES)
            .where(STOCK_TYPES.ID.eq(updated.id))
            .fetchSingle { record -> record.toResponse() }
    }

    fun deleteByExternalId(externalId: UUID): Int =
        dsl.deleteFrom(STOCK_TYPES)
            .where(STOCK_TYPES.EXTERNAL_ID.eq(externalId))
            .execute()

    private fun usageCountField() =
        DSL.field(
            DSL.selectCount()
                .from(STOCK_HOLDINGS)
                .where(STOCK_HOLDINGS.STOCK_TYPE_ID.eq(STOCK_TYPES.ID))
        ).`as`("usage_count")

    private fun Record.toResponse() = TypeResponse(
        id = get(STOCK_TYPES.EXTERNAL_ID)!!,
        name = get(STOCK_TYPES.NAME)!!,
        usageCount = get("usage_count", Int::class.java) ?: 0,
    )
}
