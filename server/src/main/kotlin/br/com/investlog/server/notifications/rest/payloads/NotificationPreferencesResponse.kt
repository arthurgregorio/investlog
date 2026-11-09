package br.com.investlog.server.notifications.rest.payloads

import java.math.BigDecimal

data class NotificationPreferencesResponse(
    val periodicReportFrequency: PeriodicReportFrequency,
    val monthEndReportEnabled: Boolean,
    val frozenAlertEnabled: Boolean,
    val frozenAlertMinimumGainPercentage: BigDecimal,
    val emailConfigured: Boolean,
)
