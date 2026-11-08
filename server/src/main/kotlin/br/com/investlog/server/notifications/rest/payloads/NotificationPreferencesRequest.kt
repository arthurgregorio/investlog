package br.com.investlog.server.notifications.rest.payloads

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import java.math.BigDecimal

data class NotificationPreferencesRequest(
    val periodicReportFrequency: PeriodicReportFrequency,
    val monthEndReportEnabled: Boolean,
    val frozenAlertEnabled: Boolean,
    @field:DecimalMin(value = "0", message = "frozenAlertMinimumGainPercentage deve estar entre 0 e 100")
    @field:DecimalMax(value = "100", message = "frozenAlertMinimumGainPercentage deve estar entre 0 e 100")
    @field:Digits(integer = 3, fraction = 2, message = "frozenAlertMinimumGainPercentage deve ter no máximo duas casas decimais")
    val frozenAlertMinimumGainPercentage: BigDecimal,
)
