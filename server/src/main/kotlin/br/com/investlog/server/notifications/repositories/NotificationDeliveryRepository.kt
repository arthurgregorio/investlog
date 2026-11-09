package br.com.investlog.server.notifications.repositories

import br.com.investlog.server.jooq.system.tables.references.NOTIFICATION_DELIVERIES
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.OffsetDateTime

@Repository
class NotificationDeliveryRepository(private val dsl: DSLContext) {

    fun claim(userId: Long, kind: NotificationDeliveryKind, periodEnd: LocalDate): Boolean =
        dsl.insertInto(NOTIFICATION_DELIVERIES)
            .set(NOTIFICATION_DELIVERIES.USER_ID, userId)
            .set(NOTIFICATION_DELIVERIES.KIND, kind.name)
            .set(NOTIFICATION_DELIVERIES.PERIOD_END, periodEnd)
            .set(NOTIFICATION_DELIVERIES.SENT_AT, OffsetDateTime.now())
            .onConflict(NOTIFICATION_DELIVERIES.USER_ID, NOTIFICATION_DELIVERIES.KIND, NOTIFICATION_DELIVERIES.PERIOD_END)
            .doNothing()
            .execute() == 1

    fun release(userId: Long, kind: NotificationDeliveryKind, periodEnd: LocalDate): Boolean =
        dsl.deleteFrom(NOTIFICATION_DELIVERIES)
            .where(NOTIFICATION_DELIVERIES.USER_ID.eq(userId))
            .and(NOTIFICATION_DELIVERIES.KIND.eq(kind.name))
            .and(NOTIFICATION_DELIVERIES.PERIOD_END.eq(periodEnd))
            .execute() == 1
}
