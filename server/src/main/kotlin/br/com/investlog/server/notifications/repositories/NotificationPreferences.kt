package br.com.investlog.server.notifications.repositories

import br.com.investlog.server.jooq.system.tables.records.NotificationPreferencesRecord
import br.com.investlog.server.notifications.rest.payloads.PeriodicReportFrequency
import java.math.BigDecimal

data class NotificationPreferences(
    val periodicReportFrequency: PeriodicReportFrequency,
    val monthEndReportEnabled: Boolean,
    val frozenAlertEnabled: Boolean,
    val frozenAlertMinimumGainPercentage: BigDecimal,
) {

    companion object {
        val DEFAULTS = NotificationPreferences(
            periodicReportFrequency = PeriodicReportFrequency.DISABLED,
            monthEndReportEnabled = true,
            frozenAlertEnabled = true,
            frozenAlertMinimumGainPercentage = BigDecimal("1.00"),
        )

        fun from(record: NotificationPreferencesRecord?): NotificationPreferences =
            if (record?.userId == null) {
                DEFAULTS
            } else {
                NotificationPreferences(
                    periodicReportFrequency = PeriodicReportFrequency.valueOf(record.periodicReportFrequency!!),
                    monthEndReportEnabled = record.monthEndReportEnabled!!,
                    frozenAlertEnabled = record.frozenAlertEnabled!!,
                    frozenAlertMinimumGainPercentage = record.frozenAlertMinimumGainPercentage!!,
                )
            }
    }
}
