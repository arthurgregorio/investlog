package br.com.investlog.server.notifications.services

import br.com.investlog.server.notifications.repositories.NotificationDeliveryKind
import br.com.investlog.server.notifications.repositories.NotificationPreferences
import br.com.investlog.server.notifications.rest.payloads.PeriodicReportFrequency

enum class PerformanceReportKind(
    val deliveryKind: NotificationDeliveryKind,
    val label: String,
    val resultLabel: String,
    val columnLabel: String,
    val ofThePeriod: String,
    val duringThePeriod: String,
    val footer: String,
) {
    DAILY(
        deliveryKind = NotificationDeliveryKind.DAILY_REPORT,
        label = "Relatório diário",
        resultLabel = "Resultado no período",
        columnLabel = "No período",
        ofThePeriod = "do período",
        duringThePeriod = "o período",
        footer = "Você recebe este relatório de terça a sábado porque escolheu a frequência diária.",
    ),
    WEEKLY(
        deliveryKind = NotificationDeliveryKind.WEEKLY_REPORT,
        label = "Relatório semanal",
        resultLabel = "Resultado no período",
        columnLabel = "Na semana",
        ofThePeriod = "da semana",
        duringThePeriod = "a semana",
        footer = "Você recebe este relatório toda segunda-feira porque escolheu a frequência semanal.",
    ),
    BIWEEKLY(
        deliveryKind = NotificationDeliveryKind.BIWEEKLY_REPORT,
        label = "Relatório quinzenal",
        resultLabel = "Resultado no período",
        columnLabel = "Na quinzena",
        ofThePeriod = "da quinzena",
        duringThePeriod = "a quinzena",
        footer = "Você recebe este relatório nos dias 1º e 16 porque escolheu a frequência quinzenal.",
    ),
    MONTH_END(
        deliveryKind = NotificationDeliveryKind.MONTH_END_REPORT,
        label = "Relatório de fim de mês",
        resultLabel = "Resultado no mês",
        columnLabel = "No mês",
        ofThePeriod = "do mês",
        duringThePeriod = "o mês",
        footer = "Você recebe o relatório de fim de mês todo dia 1º.",
    );

    fun isWantedBy(preferences: NotificationPreferences): Boolean = when (this) {
        DAILY -> preferences.periodicReportFrequency == PeriodicReportFrequency.DAILY
        WEEKLY -> preferences.periodicReportFrequency == PeriodicReportFrequency.WEEKLY
        BIWEEKLY -> preferences.periodicReportFrequency == PeriodicReportFrequency.BIWEEKLY
        MONTH_END -> preferences.monthEndReportEnabled
    }
}
