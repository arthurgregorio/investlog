package br.com.investlog.server.notifications.repositories

import br.com.investlog.server.jooq.finances.enums.HoldingStatus
import br.com.investlog.server.jooq.finances.tables.references.FROZEN_HOLDING_ALERTS
import br.com.investlog.server.jooq.finances.tables.references.HOLDINGS_OVERVIEW
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class FrozenHoldingAlertRepository(private val dsl: DSLContext) {

    fun findFrozenHoldings(userId: Long): List<FrozenHolding> =
        dsl.select(
            HOLDINGS_OVERVIEW.KIND,
            HOLDINGS_OVERVIEW.EXTERNAL_ID,
            HOLDINGS_OVERVIEW.TICKER,
            HOLDINGS_OVERVIEW.NAME,
            HOLDINGS_OVERVIEW.WALLET_NAME,
            HOLDINGS_OVERVIEW.WALLET_CURRENCY,
            HOLDINGS_OVERVIEW.CURRENT_VALUE,
            HOLDINGS_OVERVIEW.GAIN,
            HOLDINGS_OVERVIEW.GAIN_PCT,
            FROZEN_HOLDING_ALERTS.GREEN,
            FROZEN_HOLDING_ALERTS.GREEN_SINCE,
            FROZEN_HOLDING_ALERTS.ALERTED_AT,
        )
            .from(HOLDINGS_OVERVIEW)
            .leftJoin(FROZEN_HOLDING_ALERTS)
            .on(FROZEN_HOLDING_ALERTS.HOLDING_KIND.eq(HOLDINGS_OVERVIEW.KIND))
            .and(FROZEN_HOLDING_ALERTS.HOLDING_EXTERNAL_ID.eq(HOLDINGS_OVERVIEW.EXTERNAL_ID))
            .where(HOLDINGS_OVERVIEW.USER_ID.eq(userId))
            .and(HOLDINGS_OVERVIEW.FROZEN.isTrue)
            .and(HOLDINGS_OVERVIEW.STATUS.eq(HoldingStatus.ACTIVE))
            .orderBy(HOLDINGS_OVERVIEW.TICKER.nullsLast(), HOLDINGS_OVERVIEW.NAME, HOLDINGS_OVERVIEW.EXTERNAL_ID)
            .fetch { record ->
                FrozenHolding(
                    kind = record.get(HOLDINGS_OVERVIEW.KIND)!!,
                    externalId = record.get(HOLDINGS_OVERVIEW.EXTERNAL_ID)!!,
                    ticker = record.get(HOLDINGS_OVERVIEW.TICKER),
                    name = record.get(HOLDINGS_OVERVIEW.NAME)!!,
                    walletName = record.get(HOLDINGS_OVERVIEW.WALLET_NAME)!!,
                    walletCurrency = record.get(HOLDINGS_OVERVIEW.WALLET_CURRENCY)!!,
                    currentValue = record.get(HOLDINGS_OVERVIEW.CURRENT_VALUE),
                    gain = record.get(HOLDINGS_OVERVIEW.GAIN),
                    gainPercentage = record.get(HOLDINGS_OVERVIEW.GAIN_PCT),
                    alert = record.get(FROZEN_HOLDING_ALERTS.GREEN)?.let { green ->
                        FrozenHoldingAlert(
                            green = green,
                            greenSince = record.get(FROZEN_HOLDING_ALERTS.GREEN_SINCE),
                            alertedAt = record.get(FROZEN_HOLDING_ALERTS.ALERTED_AT),
                        )
                    },
                )
            }

    fun deleteAllExcept(userId: Long, holdings: List<FrozenHolding>): Int =
        dsl.deleteFrom(FROZEN_HOLDING_ALERTS)
            .where(FROZEN_HOLDING_ALERTS.USER_ID.eq(userId))
            .and(
                DSL.row(FROZEN_HOLDING_ALERTS.HOLDING_KIND, FROZEN_HOLDING_ALERTS.HOLDING_EXTERNAL_ID)
                    .notIn(holdings.map { holding -> DSL.row(holding.kind, holding.externalId) }),
            )
            .execute()

    fun save(userId: Long, holding: FrozenHolding, alert: FrozenHoldingAlert) {
        dsl.insertInto(FROZEN_HOLDING_ALERTS)
            .set(FROZEN_HOLDING_ALERTS.HOLDING_KIND, holding.kind)
            .set(FROZEN_HOLDING_ALERTS.HOLDING_EXTERNAL_ID, holding.externalId)
            .set(FROZEN_HOLDING_ALERTS.USER_ID, userId)
            .set(FROZEN_HOLDING_ALERTS.GREEN, alert.green)
            .set(FROZEN_HOLDING_ALERTS.GREEN_SINCE, alert.greenSince)
            .set(FROZEN_HOLDING_ALERTS.ALERTED_AT, alert.alertedAt)
            .onConflict(FROZEN_HOLDING_ALERTS.HOLDING_KIND, FROZEN_HOLDING_ALERTS.HOLDING_EXTERNAL_ID)
            .doUpdate()
            .set(FROZEN_HOLDING_ALERTS.USER_ID, DSL.excluded(FROZEN_HOLDING_ALERTS.USER_ID))
            .set(FROZEN_HOLDING_ALERTS.GREEN, DSL.excluded(FROZEN_HOLDING_ALERTS.GREEN))
            .set(FROZEN_HOLDING_ALERTS.GREEN_SINCE, DSL.excluded(FROZEN_HOLDING_ALERTS.GREEN_SINCE))
            .set(FROZEN_HOLDING_ALERTS.ALERTED_AT, DSL.excluded(FROZEN_HOLDING_ALERTS.ALERTED_AT))
            .execute()
    }
}
