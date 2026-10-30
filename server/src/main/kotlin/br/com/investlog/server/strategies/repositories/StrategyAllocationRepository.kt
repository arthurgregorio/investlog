package br.com.investlog.server.strategies.repositories

import br.com.investlog.server.jooq.finances.enums.HoldingStatus
import br.com.investlog.server.jooq.finances.tables.references.HOLDINGS_VALUED
import br.com.investlog.server.jooq.finances.tables.references.HOLDING_STRATEGY_CLASSES
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal

@Repository
class StrategyAllocationRepository(private val dsl: DSLContext) {

    fun findActiveHoldings(userId: Long, displayCurrencyRate: BigDecimal): List<ValuedHoldingRow> {
        val holdingClasses = HOLDING_STRATEGY_CLASSES.`as`("holding_classes")
        val valued = HOLDINGS_VALUED.`as`("valued")
        val displayValue = DSL.coalesce(valued.CURRENT_VALUE, BigDecimal.ZERO).mul(valued.RATE).div(displayCurrencyRate)

        return dsl.select(
            valued.KIND,
            valued.EXTERNAL_ID,
            valued.WALLET_EXTERNAL_ID,
            valued.WALLET_NAME,
            valued.TICKER,
            valued.NAME,
            valued.TYPE_LABEL,
            valued.SEGMENT_LABEL,
            valued.FROZEN,
            holdingClasses.STRATEGY_CLASS_ID,
            holdingClasses.CLASS_TARGET_PERCENTAGE,
            displayValue.`as`("display_value"),
        )
            .from(holdingClasses)
            .join(valued).on(valued.KIND.eq(holdingClasses.KIND)).and(valued.HOLDING_ID.eq(holdingClasses.HOLDING_ID))
            .where(holdingClasses.USER_ID.eq(userId))
            .and(valued.STATUS.eq(HoldingStatus.ACTIVE))
            .orderBy(DSL.lower(DSL.coalesce(valued.TICKER, valued.NAME)).asc(), valued.EXTERNAL_ID.asc())
            .fetch { record ->
                ValuedHoldingRow(
                    kind = record.get(valued.KIND)!!,
                    externalId = record.get(valued.EXTERNAL_ID)!!,
                    walletExternalId = record.get(valued.WALLET_EXTERNAL_ID)!!,
                    walletName = record.get(valued.WALLET_NAME)!!,
                    ticker = record.get(valued.TICKER),
                    name = record.get(valued.NAME)!!,
                    typeLabel = record.get(valued.TYPE_LABEL),
                    segmentLabel = record.get(valued.SEGMENT_LABEL),
                    frozen = record.get(valued.FROZEN)!!,
                    strategyClassId = record.get(holdingClasses.STRATEGY_CLASS_ID)!!,
                    targetPercentage = record.get(holdingClasses.CLASS_TARGET_PERCENTAGE),
                    value = record.get("display_value", BigDecimal::class.java)!!,
                )
            }
    }
}
