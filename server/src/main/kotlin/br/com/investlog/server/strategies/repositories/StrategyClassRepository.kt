package br.com.investlog.server.strategies.repositories

import br.com.investlog.server.jooq.finances.tables.references.STRATEGIES
import br.com.investlog.server.jooq.finances.tables.references.STRATEGY_CLASSES
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.util.UUID

@Repository
class StrategyClassRepository(private val dsl: DSLContext) {

    fun findByStrategies(strategyIds: Collection<Long>): List<StrategyClassRow> =
        dsl.selectFrom(STRATEGY_CLASSES)
            .where(STRATEGY_CLASSES.STRATEGY_ID.`in`(strategyIds))
            .orderBy(STRATEGY_CLASSES.POSITION.asc(), STRATEGY_CLASSES.ID.asc())
            .fetch { record -> toRow(record) }

    fun findByExternalId(userId: Long, externalId: UUID): StrategyClassRow? =
        dsl.select(STRATEGY_CLASSES.asterisk())
            .from(STRATEGY_CLASSES)
            .join(STRATEGIES).on(STRATEGIES.ID.eq(STRATEGY_CLASSES.STRATEGY_ID))
            .where(STRATEGIES.USER_ID.eq(userId))
            .and(STRATEGY_CLASSES.EXTERNAL_ID.eq(externalId))
            .fetchOne { record -> toRow(record) }

    fun findDefaultClassId(userId: Long): Long =
        dsl.select(STRATEGY_CLASSES.ID)
            .from(STRATEGY_CLASSES)
            .join(STRATEGIES).on(STRATEGIES.ID.eq(STRATEGY_CLASSES.STRATEGY_ID))
            .where(STRATEGIES.USER_ID.eq(userId))
            .and(STRATEGIES.IS_DEFAULT.isTrue)
            .and(STRATEGY_CLASSES.IS_DEFAULT.isTrue)
            .fetchSingle(STRATEGY_CLASSES.ID)!!

    fun insert(strategyId: Long, name: String, targetPercentage: BigDecimal, position: Int) {
        dsl.insertInto(STRATEGY_CLASSES)
            .set(STRATEGY_CLASSES.STRATEGY_ID, strategyId)
            .set(STRATEGY_CLASSES.NAME, name)
            .set(STRATEGY_CLASSES.TARGET_PERCENTAGE, targetPercentage)
            .set(STRATEGY_CLASSES.POSITION, position)
            .execute()
    }

    fun update(id: Long, name: String, targetPercentage: BigDecimal, position: Int) {
        dsl.update(STRATEGY_CLASSES)
            .set(STRATEGY_CLASSES.NAME, name)
            .set(STRATEGY_CLASSES.TARGET_PERCENTAGE, targetPercentage)
            .set(STRATEGY_CLASSES.POSITION, position)
            .where(STRATEGY_CLASSES.ID.eq(id))
            .execute()
    }

    fun releaseNames(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        dsl.update(STRATEGY_CLASSES)
            .set(STRATEGY_CLASSES.NAME, STRATEGY_CLASSES.EXTERNAL_ID.cast(String::class.java))
            .where(STRATEGY_CLASSES.ID.`in`(ids))
            .execute()
    }

    fun delete(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        dsl.deleteFrom(STRATEGY_CLASSES).where(STRATEGY_CLASSES.ID.`in`(ids)).execute()
    }

    private fun toRow(record: Record): StrategyClassRow =
        StrategyClassRow(
            id = record.get(STRATEGY_CLASSES.ID)!!,
            externalId = record.get(STRATEGY_CLASSES.EXTERNAL_ID)!!,
            strategyId = record.get(STRATEGY_CLASSES.STRATEGY_ID)!!,
            name = record.get(STRATEGY_CLASSES.NAME)!!,
            targetPercentage = record.get(STRATEGY_CLASSES.TARGET_PERCENTAGE),
            position = record.get(STRATEGY_CLASSES.POSITION)!!,
            isDefault = record.get(STRATEGY_CLASSES.IS_DEFAULT)!!,
        )
}
