package br.com.investlog.server.notifications.repositories

import br.com.investlog.server.jooq.system.tables.references.NOTIFICATION_PREFERENCES
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
class NotificationPreferencesRepository(private val dsl: DSLContext) {

    fun find(userId: Long): NotificationPreferences =
        NotificationPreferences.from(dsl.fetchOne(NOTIFICATION_PREFERENCES, NOTIFICATION_PREFERENCES.USER_ID.eq(userId)))

    fun save(userId: Long, preferences: NotificationPreferences): NotificationPreferences =
        dsl.insertInto(NOTIFICATION_PREFERENCES)
            .set(NOTIFICATION_PREFERENCES.USER_ID, userId)
            .set(NOTIFICATION_PREFERENCES.PERIODIC_REPORT_FREQUENCY, preferences.periodicReportFrequency.name)
            .set(NOTIFICATION_PREFERENCES.MONTH_END_REPORT_ENABLED, preferences.monthEndReportEnabled)
            .set(NOTIFICATION_PREFERENCES.FROZEN_ALERT_ENABLED, preferences.frozenAlertEnabled)
            .set(NOTIFICATION_PREFERENCES.FROZEN_ALERT_MINIMUM_GAIN_PERCENTAGE, preferences.frozenAlertMinimumGainPercentage)
            .set(NOTIFICATION_PREFERENCES.UPDATED_AT, OffsetDateTime.now())
            .onConflict(NOTIFICATION_PREFERENCES.USER_ID)
            .doUpdate()
            .set(NOTIFICATION_PREFERENCES.PERIODIC_REPORT_FREQUENCY, DSL.excluded(NOTIFICATION_PREFERENCES.PERIODIC_REPORT_FREQUENCY))
            .set(NOTIFICATION_PREFERENCES.MONTH_END_REPORT_ENABLED, DSL.excluded(NOTIFICATION_PREFERENCES.MONTH_END_REPORT_ENABLED))
            .set(NOTIFICATION_PREFERENCES.FROZEN_ALERT_ENABLED, DSL.excluded(NOTIFICATION_PREFERENCES.FROZEN_ALERT_ENABLED))
            .set(
                NOTIFICATION_PREFERENCES.FROZEN_ALERT_MINIMUM_GAIN_PERCENTAGE,
                DSL.excluded(NOTIFICATION_PREFERENCES.FROZEN_ALERT_MINIMUM_GAIN_PERCENTAGE),
            )
            .set(NOTIFICATION_PREFERENCES.UPDATED_AT, DSL.excluded(NOTIFICATION_PREFERENCES.UPDATED_AT))
            .returning()
            .fetchSingle()
            .let(NotificationPreferences::from)
}
