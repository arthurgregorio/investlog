package br.com.investlog.server.typelists.repositories

import br.com.investlog.server.jooq.finances.tables.references.FUND_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.FUND_TYPES
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
class FundTypeRepository(private val dsl: DSLContext) {

    fun findAll(pageable: Pageable): PagedModel<TypeResponse> {
        val content = dsl.select(FUND_TYPES.EXTERNAL_ID, FUND_TYPES.NAME, usageCountField())
            .from(FUND_TYPES)
            .orderBy(FUND_TYPES.NAME)
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch { record -> record.toResponse() }

        val total = dsl.fetchCount(FUND_TYPES)

        return pagedModelOf(content, pageable, total.toLong())
    }

    fun create(name: String): TypeResponse {
        val fundType = dsl.insertInto(FUND_TYPES)
            .set(FUND_TYPES.NAME, name)
            .returning()
            .fetchSingle()

        return dsl.select(FUND_TYPES.EXTERNAL_ID, FUND_TYPES.NAME, usageCountField())
            .from(FUND_TYPES)
            .where(FUND_TYPES.ID.eq(fundType.id))
            .fetchSingle { record -> record.toResponse() }
    }

    fun update(externalId: UUID, name: String): TypeResponse? {
        val updated = dsl.update(FUND_TYPES)
            .set(FUND_TYPES.NAME, name)
            .where(FUND_TYPES.EXTERNAL_ID.eq(externalId))
            .returning(FUND_TYPES.ID)
            .fetchOne() ?: return null

        return dsl.select(FUND_TYPES.EXTERNAL_ID, FUND_TYPES.NAME, usageCountField())
            .from(FUND_TYPES)
            .where(FUND_TYPES.ID.eq(updated.id))
            .fetchSingle { record -> record.toResponse() }
    }

    fun deleteByExternalId(externalId: UUID): Int =
        dsl.deleteFrom(FUND_TYPES)
            .where(FUND_TYPES.EXTERNAL_ID.eq(externalId))
            .execute()

    private fun usageCountField() =
        DSL.field(
            DSL.selectCount()
                .from(FUND_HOLDINGS)
                .where(FUND_HOLDINGS.FUND_TYPE_ID.eq(FUND_TYPES.ID))
        ).`as`("usage_count")

    private fun Record.toResponse() = TypeResponse(
        id = get(FUND_TYPES.EXTERNAL_ID)!!,
        name = get(FUND_TYPES.NAME)!!,
        usageCount = get("usage_count", Int::class.java) ?: 0,
    )
}
