package br.com.investlog.server.notifications.services

import br.com.investlog.server.notifications.repositories.NotificationPreferences
import br.com.investlog.server.notifications.rest.payloads.PeriodicReportFrequency
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReportPeriodTest {

    @Test
    fun `daily reports are due Tuesday to Saturday and Tuesday's compares Monday with Friday`() {
        assertEquals(
            listOf(period(PerformanceReportKind.DAILY, "2026-10-30", "2026-11-02")),
            ReportPeriod.dueOn(LocalDate.parse("2026-11-03")),
        )
        assertEquals(
            listOf(period(PerformanceReportKind.DAILY, "2026-11-02", "2026-11-03")),
            ReportPeriod.dueOn(LocalDate.parse("2026-11-04")),
        )
        assertEquals(
            listOf(period(PerformanceReportKind.DAILY, "2026-11-05", "2026-11-06")),
            ReportPeriod.dueOn(LocalDate.parse("2026-11-07")),
        )
        assertEquals(emptyList(), ReportPeriod.dueOn(LocalDate.parse("2026-11-08")))
    }

    @Test
    fun `weekly reports are due on Monday and compare last Sunday with the Sunday before`() {
        assertEquals(
            listOf(period(PerformanceReportKind.WEEKLY, "2026-11-01", "2026-11-08")),
            ReportPeriod.dueOn(LocalDate.parse("2026-11-09")),
        )
    }

    @Test
    fun `fifteen-day reports are due on the 1st and the 16th`() {
        assertEquals(
            listOf(
                period(PerformanceReportKind.WEEKLY, "2026-11-08", "2026-11-15"),
                period(PerformanceReportKind.BIWEEKLY, "2026-10-31", "2026-11-15"),
            ),
            ReportPeriod.dueOn(LocalDate.parse("2026-11-16")),
        )
        assertEquals(
            listOf(
                period(PerformanceReportKind.DAILY, "2026-11-27", "2026-11-30"),
                period(PerformanceReportKind.BIWEEKLY, "2026-11-15", "2026-11-30"),
                period(PerformanceReportKind.MONTH_END, "2026-10-31", "2026-11-30"),
            ),
            ReportPeriod.dueOn(LocalDate.parse("2026-12-01")),
        )
    }

    @Test
    fun `month-end reports are due on the 1st and compare the last two month ends`() {
        assertEquals(
            listOf(
                period(PerformanceReportKind.BIWEEKLY, "2026-02-15", "2026-02-28"),
                period(PerformanceReportKind.MONTH_END, "2026-01-31", "2026-02-28"),
            ),
            ReportPeriod.dueOn(LocalDate.parse("2026-03-01")),
        )
        assertEquals(
            listOf(
                period(PerformanceReportKind.DAILY, "2027-12-30", "2027-12-31"),
                period(PerformanceReportKind.BIWEEKLY, "2027-12-15", "2027-12-31"),
                period(PerformanceReportKind.MONTH_END, "2027-11-30", "2027-12-31"),
            ),
            ReportPeriod.dueOn(LocalDate.parse("2028-01-01")),
        )
    }

    @Test
    fun `consecutive periods of every kind share their boundary snapshot`() {
        val periodsByKind = generateSequence(LocalDate.parse("2026-01-01")) { day -> day.plusDays(1) }
            .takeWhile { day -> day.year < 2028 }
            .flatMap { day -> ReportPeriod.dueOn(day) }
            .groupBy { period -> period.kind }

        assertEquals(PerformanceReportKind.entries.toSet(), periodsByKind.keys)

        periodsByKind.values.forEach { periods ->
            periods.zipWithNext().forEach { (previous, next) ->
                assertEquals(previous.endDate, next.startDate)
            }
        }

        assertTrue(periodsByKind.getValue(PerformanceReportKind.DAILY).all { period -> period.endDate.dayOfWeek <= DayOfWeek.FRIDAY })
    }

    @Test
    fun `each kind is wanted only by the matching preferences`() {
        val daily = preferences(PeriodicReportFrequency.DAILY, monthEndReportEnabled = false)
        val weekly = preferences(PeriodicReportFrequency.WEEKLY, monthEndReportEnabled = true)
        val biweekly = preferences(PeriodicReportFrequency.BIWEEKLY, monthEndReportEnabled = false)

        assertTrue(PerformanceReportKind.DAILY.isWantedBy(daily))
        assertFalse(PerformanceReportKind.DAILY.isWantedBy(weekly))
        assertTrue(PerformanceReportKind.WEEKLY.isWantedBy(weekly))
        assertFalse(PerformanceReportKind.WEEKLY.isWantedBy(biweekly))
        assertTrue(PerformanceReportKind.BIWEEKLY.isWantedBy(biweekly))
        assertFalse(PerformanceReportKind.BIWEEKLY.isWantedBy(daily))
        assertTrue(PerformanceReportKind.MONTH_END.isWantedBy(weekly))
        assertFalse(PerformanceReportKind.MONTH_END.isWantedBy(daily))
        assertFalse(PerformanceReportKind.DAILY.isWantedBy(NotificationPreferences.DEFAULTS))
        assertTrue(PerformanceReportKind.MONTH_END.isWantedBy(NotificationPreferences.DEFAULTS))
    }

    private fun period(kind: PerformanceReportKind, startDate: String, endDate: String) =
        ReportPeriod(kind, LocalDate.parse(startDate), LocalDate.parse(endDate))

    private fun preferences(frequency: PeriodicReportFrequency, monthEndReportEnabled: Boolean) =
        NotificationPreferences.DEFAULTS.copy(periodicReportFrequency = frequency, monthEndReportEnabled = monthEndReportEnabled)
}
