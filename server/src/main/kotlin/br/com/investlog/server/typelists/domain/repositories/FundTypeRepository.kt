package br.com.investlog.server.typelists.domain.repositories

import br.com.investlog.server.jooq.finances.tables.records.FundTypesRecord
import br.com.investlog.server.jooq.finances.tables.references.FUND_TYPES
import br.com.investlog.server.shared.persistence.pagedModelOf
import br.com.investlog.server.typelists.rest.payloads.TypeResponse
import org.jooq.DSLContext
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class FundTypeRepository(private val dsl: DSLContext) {

    fun findAll(userId: Long, pageable: Pageable): PagedModel<TypeResponse> {
        val content = dsl.selectFrom(FUND_TYPES)
            .where(FUND_TYPES.USER_ID.eq(userId))
            .orderBy(FUND_TYPES.NAME)
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch()
            .map { it.toResponse() }

        val total = dsl.fetchCount(
            dsl.selectFrom(FUND_TYPES).where(FUND_TYPES.USER_ID.eq(userId))
        )

        return pagedModelOf(content, pageable, total.toLong())
    }

    fun create(userId: Long, name: String): TypeResponse =
        dsl.insertInto(FUND_TYPES)
            .set(FUND_TYPES.USER_ID, userId)
            .set(FUND_TYPES.NAME, name)
            .returning()
            .fetchSingle()
            .toResponse()

    fun deleteByExternalId(userId: Long, externalId: UUID): Int =
        dsl.deleteFrom(FUND_TYPES)
            .where(FUND_TYPES.USER_ID.eq(userId))
            .and(FUND_TYPES.EXTERNAL_ID.eq(externalId))
            .execute()

    private fun FundTypesRecord.toResponse() = TypeResponse(
        id = externalId!!,
        name = name!!,
    )
}
