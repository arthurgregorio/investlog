package br.com.investlog.server.typelists.repositories

import br.com.investlog.server.jooq.finances.tables.records.StockTypesRecord
import br.com.investlog.server.jooq.finances.tables.references.STOCK_TYPES
import br.com.investlog.server.shared.utils.pagedModelOf
import br.com.investlog.server.typelists.rest.payloads.TypeResponse
import org.jooq.DSLContext
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class StockTypeRepository(private val dsl: DSLContext) {

    fun findAll(pageable: Pageable): PagedModel<TypeResponse> {
        val content = dsl.selectFrom(STOCK_TYPES)
            .orderBy(STOCK_TYPES.NAME)
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch()
            .map { it.toResponse() }

        val total = dsl.fetchCount(STOCK_TYPES)

        return pagedModelOf(content, pageable, total.toLong())
    }

    fun create(name: String): TypeResponse =
        dsl.insertInto(STOCK_TYPES)
            .set(STOCK_TYPES.NAME, name)
            .returning()
            .fetchSingle()
            .toResponse()

    fun deleteByExternalId(externalId: UUID): Int =
        dsl.deleteFrom(STOCK_TYPES)
            .where(STOCK_TYPES.EXTERNAL_ID.eq(externalId))
            .execute()

    private fun StockTypesRecord.toResponse() = TypeResponse(
        id = externalId!!,
        name = name!!,
    )
}
