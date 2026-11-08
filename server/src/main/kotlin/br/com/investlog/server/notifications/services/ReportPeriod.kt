package br.com.investlog.server.notifications.services

import java.time.DayOfWeek
import java.time.LocalDate

data class ReportPeriod(
    val kind: PerformanceReportKind,
    val startDate: LocalDate,
    val endDate: LocalDate,
) {

    companion object {
        private const val SECOND_HALF_FIRST_DAY = 16
        private const val FIRST_HALF_LAST_DAY = 15

        fun dueOn(today: LocalDate): List<ReportPeriod> {
            val yesterday = today.minusDays(1)

            return listOfNotNull(
                daily(today, yesterday),
                weekly(today, yesterday),
                biweekly(today, yesterday),
                monthEnd(today, yesterday),
            )
        }

        private fun daily(today: LocalDate, yesterday: LocalDate): ReportPeriod? =
            if (today.dayOfWeek in DayOfWeek.TUESDAY..DayOfWeek.SATURDAY) {
                val businessDayBefore = if (yesterday.dayOfWeek == DayOfWeek.MONDAY) yesterday.minusDays(3) else yesterday.minusDays(1)
                ReportPeriod(PerformanceReportKind.DAILY, businessDayBefore, yesterday)
            } else {
                null
            }

        private fun weekly(today: LocalDate, yesterday: LocalDate): ReportPeriod? =
            if (today.dayOfWeek == DayOfWeek.MONDAY) ReportPeriod(PerformanceReportKind.WEEKLY, yesterday.minusDays(7), yesterday) else null

        private fun biweekly(today: LocalDate, yesterday: LocalDate): ReportPeriod? = when (today.dayOfMonth) {
            1 -> ReportPeriod(PerformanceReportKind.BIWEEKLY, yesterday.withDayOfMonth(FIRST_HALF_LAST_DAY), yesterday)
            SECOND_HALF_FIRST_DAY -> ReportPeriod(PerformanceReportKind.BIWEEKLY, today.withDayOfMonth(1).minusDays(1), yesterday)
            else -> null
        }

        private fun monthEnd(today: LocalDate, yesterday: LocalDate): ReportPeriod? =
            if (today.dayOfMonth == 1) ReportPeriod(PerformanceReportKind.MONTH_END, yesterday.withDayOfMonth(1).minusDays(1), yesterday) else null
    }
}
