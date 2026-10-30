package br.com.investlog.server.strategies.repositories

import br.com.investlog.server.jooq.finances.enums.HoldingStatus
import br.com.investlog.server.jooq.finances.enums.WalletKind
import br.com.investlog.server.jooq.finances.tables.references.CRYPTO_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.FUND_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.HOLDINGS_OVERVIEW
import br.com.investlog.server.jooq.finances.tables.references.HOLDING_STRATEGY_CLASSES
import br.com.investlog.server.jooq.finances.tables.references.STOCK_HOLDINGS
import br.com.investlog.server.strategies.rest.payloads.ClassHoldingResponse
import org.jooq.DSLContext
import org.jooq.Table
import org.jooq.TableField
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.util.UUID

@Repository
class HoldingClassRepository(private val dsl: DSLContext) {

    private data class HoldingTable(
        val table: Table<*>,
        val id: TableField<*, Long?>,
        val strategyClassId: TableField<*, Long?>,
        val classTargetPercentage: TableField<*, BigDecimal?>,
    )

    private val tables = mapOf(
        WalletKind.STOCKS to HoldingTable(STOCK_HOLDINGS, STOCK_HOLDINGS.ID, STOCK_HOLDINGS.STRATEGY_CLASS_ID, STOCK_HOLDINGS.CLASS_TARGET_PERCENTAGE),
        WalletKind.CRYPTO to HoldingTable(CRYPTO_HOLDINGS, CRYPTO_HOLDINGS.ID, CRYPTO_HOLDINGS.STRATEGY_CLASS_ID, CRYPTO_HOLDINGS.CLASS_TARGET_PERCENTAGE),
        WalletKind.FUNDS to HoldingTable(FUND_HOLDINGS, FUND_HOLDINGS.ID, FUND_HOLDINGS.STRATEGY_CLASS_ID, FUND_HOLDINGS.CLASS_TARGET_PERCENTAGE),
    )

    fun findActiveTargets(strategyClassIds: Collection<Long>): List<HoldingTargetRow> {
        val holdingClasses = HOLDING_STRATEGY_CLASSES.`as`("holding_classes")
        val overview = HOLDINGS_OVERVIEW.`as`("overview")
        return dsl.select(
            holdingClasses.KIND,
            holdingClasses.HOLDING_ID,
            holdingClasses.HOLDING_EXTERNAL_ID,
            holdingClasses.STRATEGY_CLASS_ID,
            holdingClasses.CLASS_TARGET_PERCENTAGE,
        )
            .from(holdingClasses)
            .join(overview).on(overview.KIND.eq(holdingClasses.KIND)).and(overview.HOLDING_ID.eq(holdingClasses.HOLDING_ID))
            .where(holdingClasses.STRATEGY_CLASS_ID.`in`(strategyClassIds))
            .and(overview.STATUS.eq(HoldingStatus.ACTIVE))
            .orderBy(holdingClasses.HOLDING_ID.asc())
            .fetch { record ->
                HoldingTargetRow(
                    kind = record.get(holdingClasses.KIND)!!,
                    holdingId = record.get(holdingClasses.HOLDING_ID)!!,
                    holdingExternalId = record.get(holdingClasses.HOLDING_EXTERNAL_ID)!!,
                    strategyClassId = record.get(holdingClasses.STRATEGY_CLASS_ID)!!,
                    targetPercentage = record.get(holdingClasses.CLASS_TARGET_PERCENTAGE),
                )
            }
    }

    fun findClassHoldings(strategyClassId: Long, effectiveTargets: (List<BigDecimal?>) -> List<BigDecimal>): List<ClassHoldingResponse> {
        val holdingClasses = HOLDING_STRATEGY_CLASSES.`as`("holding_classes")
        val overview = HOLDINGS_OVERVIEW.`as`("overview")
        val rows = dsl.select(
            overview.KIND,
            overview.EXTERNAL_ID,
            overview.WALLET_EXTERNAL_ID,
            overview.WALLET_NAME,
            overview.TICKER,
            overview.NAME,
            overview.TYPE_LABEL,
            overview.SEGMENT_LABEL,
            overview.FROZEN,
            holdingClasses.CLASS_TARGET_PERCENTAGE,
        )
            .from(holdingClasses)
            .join(overview).on(overview.KIND.eq(holdingClasses.KIND)).and(overview.HOLDING_ID.eq(holdingClasses.HOLDING_ID))
            .where(holdingClasses.STRATEGY_CLASS_ID.eq(strategyClassId))
            .and(overview.STATUS.eq(HoldingStatus.ACTIVE))
            .orderBy(DSL.lower(DSL.coalesce(overview.TICKER, overview.NAME)).asc(), overview.EXTERNAL_ID.asc())
            .fetch()

        val effective = effectiveTargets(rows.map { record -> record.get(holdingClasses.CLASS_TARGET_PERCENTAGE) })
        return rows.mapIndexed { index, record ->
            ClassHoldingResponse(
                kind = record.get(overview.KIND)!!.literal,
                id = record.get(overview.EXTERNAL_ID)!!,
                walletId = record.get(overview.WALLET_EXTERNAL_ID)!!,
                walletName = record.get(overview.WALLET_NAME)!!,
                ticker = record.get(overview.TICKER),
                name = record.get(overview.NAME)!!,
                typeLabel = record.get(overview.TYPE_LABEL),
                segmentLabel = record.get(overview.SEGMENT_LABEL),
                frozen = record.get(overview.FROZEN)!!,
                targetPercentage = record.get(holdingClasses.CLASS_TARGET_PERCENTAGE),
                effectiveTargetPercentage = effective[index],
            )
        }
    }

    fun findInternalId(userId: Long, kind: WalletKind, externalId: UUID): Long? =
        dsl.select(HOLDING_STRATEGY_CLASSES.HOLDING_ID)
            .from(HOLDING_STRATEGY_CLASSES)
            .where(HOLDING_STRATEGY_CLASSES.USER_ID.eq(userId))
            .and(HOLDING_STRATEGY_CLASSES.KIND.eq(kind))
            .and(HOLDING_STRATEGY_CLASSES.HOLDING_EXTERNAL_ID.eq(externalId))
            .fetchOne(HOLDING_STRATEGY_CLASSES.HOLDING_ID)

    fun assign(kind: WalletKind, holdingId: Long, strategyClassId: Long) {
        val holdingTable = tables.getValue(kind)
        dsl.update(holdingTable.table)
            .set(holdingTable.strategyClassId, strategyClassId)
            .set(holdingTable.classTargetPercentage, null as BigDecimal?)
            .where(holdingTable.id.eq(holdingId))
            .execute()
    }

    fun setTarget(kind: WalletKind, holdingId: Long, targetPercentage: BigDecimal?) {
        val holdingTable = tables.getValue(kind)
        dsl.update(holdingTable.table)
            .set(holdingTable.classTargetPercentage, targetPercentage)
            .where(holdingTable.id.eq(holdingId))
            .execute()
    }

    fun moveAll(fromStrategyClassIds: Collection<Long>, toStrategyClassId: Long) {
        if (fromStrategyClassIds.isEmpty()) return
        tables.values.forEach { holdingTable ->
            dsl.update(holdingTable.table)
                .set(holdingTable.strategyClassId, toStrategyClassId)
                .set(holdingTable.classTargetPercentage, null as BigDecimal?)
                .where(holdingTable.strategyClassId.`in`(fromStrategyClassIds))
                .execute()
        }
    }

}
