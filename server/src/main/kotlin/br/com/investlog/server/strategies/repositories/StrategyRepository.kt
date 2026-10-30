package br.com.investlog.server.strategies.repositories

import br.com.investlog.server.jooq.finances.tables.references.STRATEGIES
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class StrategyRepository(private val dsl: DSLContext) {

    fun findAll(userId: Long): List<StrategyRow> =
        dsl.selectFrom(STRATEGIES)
            .where(STRATEGIES.USER_ID.eq(userId))
            .orderBy(STRATEGIES.IS_DEFAULT.asc(), STRATEGIES.CREATED_AT.asc(), STRATEGIES.ID.asc())
            .fetch { record -> StrategyRow(record.id!!, record.externalId!!, record.name!!, record.isDefault!!) }

    fun findByExternalId(userId: Long, externalId: UUID): StrategyRow? =
        dsl.selectFrom(STRATEGIES)
            .where(STRATEGIES.USER_ID.eq(userId))
            .and(STRATEGIES.EXTERNAL_ID.eq(externalId))
            .fetchOne { record -> StrategyRow(record.id!!, record.externalId!!, record.name!!, record.isDefault!!) }

    fun create(userId: Long, name: String): Long =
        dsl.insertInto(STRATEGIES)
            .set(STRATEGIES.USER_ID, userId)
            .set(STRATEGIES.NAME, name)
            .returning(STRATEGIES.ID)
            .fetchSingle(STRATEGIES.ID)!!

    fun rename(id: Long, name: String) {
        dsl.update(STRATEGIES)
            .set(STRATEGIES.NAME, name)
            .set(STRATEGIES.UPDATED_AT, OffsetDateTime.now())
            .where(STRATEGIES.ID.eq(id))
            .execute()
    }

    fun touch(id: Long) {
        dsl.update(STRATEGIES)
            .set(STRATEGIES.UPDATED_AT, OffsetDateTime.now())
            .where(STRATEGIES.ID.eq(id))
            .execute()
    }

    fun delete(id: Long) {
        dsl.deleteFrom(STRATEGIES).where(STRATEGIES.ID.eq(id)).execute()
    }
}
