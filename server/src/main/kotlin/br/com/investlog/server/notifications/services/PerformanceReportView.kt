package br.com.investlog.server.notifications.services

import br.com.investlog.server.overview.rest.payloads.MonthSummaryResponse
import br.com.investlog.server.overview.rest.payloads.MoverResponse
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class PerformanceReportView(
    val subject: String,
    val label: String,
    val title: String,
    val subtitle: String,
    val resultLabel: String,
    val columnLabel: String,
    val periodResult: Figure,
    val totalValue: String,
    val currentResult: String,
    val month: Month?,
    val rows: List<Row>,
    val total: Row,
    val explanation: String,
    val footer: String,
) {

    data class Figure(val text: String, val color: String)

    data class Row(
        val name: String,
        val note: String?,
        val currentValue: String,
        val currentResult: String,
        val periodResult: Figure,
        val gainPercentage: String,
    )

    data class Month(
        val range: String,
        val contributions: Figure,
        val realizedProfit: Figure,
        val currentResultChange: Figure,
        val changeIconColor: String,
        val bestMover: Mover,
        val worstMover: Mover,
    )

    data class Mover(
        val label: String,
        val labelColor: String,
        val background: String,
        val name: String,
        val detail: String?,
        val value: Figure,
    )

    companion object {

        const val UP_COLOR = "#11804f"
        const val DOWN_COLOR = "#c42b43"
        const val TEXT_COLOR = "#1d273b"
        const val MUTED_COLOR = "#5f6b7a"
        private const val UP_BACKGROUND = "#e7f5ee"
        private const val DOWN_BACKGROUND = "#fcebee"
        private const val MISSING_BACKGROUND = "#f1f4f7"
        private const val MINUS = "−"
        private const val MISSING_VALUE = "—"

        private val PORTUGUESE_BRAZIL: Locale = Locale.forLanguageTag("pt-BR")
        private val DAY_AND_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")
        private val FULL_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        private val CURRENCY_SYMBOLS = mapOf("BRL" to "R$", "USD" to "US$")

        fun of(report: PerformanceReport): PerformanceReportView {
            val kind = report.period.kind
            val displayCurrency = report.displayCurrency

            return PerformanceReportView(
                subject = subjectOf(report.period),
                label = kind.label,
                title = titleOf(report.period),
                subtitle = subtitleOf(report.period),
                resultLabel = kind.resultLabel,
                columnLabel = kind.columnLabel,
                periodResult = signedFigure(report.totalPeriodResult, displayCurrency),
                totalValue = money(report.totalValue, displayCurrency),
                currentResult = money(report.totalCurrentResult, displayCurrency),
                month = report.month?.let { month -> monthOf(month, report.period, displayCurrency) },
                rows = report.wallets.map { wallet -> rowOf(wallet, displayCurrency) },
                total = Row(
                    name = "Total",
                    note = "na sua moeda ($displayCurrency)",
                    currentValue = money(report.totalValue, displayCurrency),
                    currentResult = money(report.totalCurrentResult, displayCurrency),
                    periodResult = signedFigure(report.totalPeriodResult, displayCurrency),
                    gainPercentage = report.totalGainPercentage?.let(::percentage) ?: MISSING_VALUE,
                ),
                explanation = explanationOf(report),
                footer = kind.footer,
            )
        }

        fun money(value: BigDecimal, currency: String): String =
            signOf(value, withPlus = false) + "${CURRENCY_SYMBOLS[currency] ?: currency} ${decimal(value)}"

        fun signedMoney(value: BigDecimal, currency: String): String =
            signOf(value, withPlus = true) + "${CURRENCY_SYMBOLS[currency] ?: currency} ${decimal(value)}"

        fun percentage(value: BigDecimal): String = signOf(value, withPlus = false) + "${decimal(value)}%"

        fun signedPercentage(value: BigDecimal): String = signOf(value, withPlus = true) + "${decimal(value)}%"

        private fun subjectOf(period: ReportPeriod): String {
            val firstDay = period.startDate.plusDays(1)
            val end = period.endDate

            return when (period.kind) {
                PerformanceReportKind.DAILY -> "Relatório diário · ${end.format(DAY_AND_MONTH)}"
                PerformanceReportKind.WEEKLY -> "Relatório semanal · ${firstDay.format(DAY_AND_MONTH)} a ${end.format(DAY_AND_MONTH)}"
                PerformanceReportKind.BIWEEKLY -> "Relatório quinzenal · ${firstDay.dayOfMonth} a ${end.format(DAY_AND_MONTH)}"
                PerformanceReportKind.MONTH_END -> "Relatório de ${monthName(end)} de ${end.year}"
            }
        }

        private fun titleOf(period: ReportPeriod): String {
            val firstDay = period.startDate.plusDays(1)
            val end = period.endDate

            return when (period.kind) {
                PerformanceReportKind.DAILY -> "${weekday(end).replaceFirstChar(Char::uppercase)}, ${end.format(FULL_DATE)}"
                PerformanceReportKind.WEEKLY -> "Semana de ${firstDay.format(DAY_AND_MONTH)} a ${end.format(DAY_AND_MONTH)}"
                PerformanceReportKind.BIWEEKLY -> "${firstDay.dayOfMonth} a ${end.dayOfMonth} de ${monthName(end)} de ${end.year}"
                PerformanceReportKind.MONTH_END -> "${monthName(end).replaceFirstChar(Char::uppercase)} de ${end.year}"
            }
        }

        private fun subtitleOf(period: ReportPeriod): String {
            val start = period.startDate
            val end = period.endDate

            return when (period.kind) {
                PerformanceReportKind.DAILY -> "Comparado com o fechamento de ${weekday(start)}, ${start.format(FULL_DATE)}."
                PerformanceReportKind.WEEKLY ->
                    "Fechamento de ${weekday(end)}, ${end.format(DAY_AND_MONTH)}, comparado com o de ${start.format(DAY_AND_MONTH)}."
                PerformanceReportKind.BIWEEKLY -> "Fechamento de ${end.format(DAY_AND_MONTH)} comparado com o de ${start.format(DAY_AND_MONTH)}."
                PerformanceReportKind.MONTH_END -> "Fechamento de ${end.format(FULL_DATE)} comparado com o de ${start.format(FULL_DATE)}."
            }
        }

        private fun rowOf(wallet: PerformanceReportWallet, displayCurrency: String): Row = Row(
            name = wallet.name,
            note = listOfNotNull(
                "em ${wallet.currency}".takeIf { wallet.currency != displayCurrency },
                wallet.newSince?.let { date -> "nova, desde ${date.format(DAY_AND_MONTH)}" },
            ).joinToString(" · ").ifEmpty { null },
            currentValue = money(wallet.currentValue, wallet.currency),
            currentResult = money(wallet.currentResult, wallet.currency),
            periodResult = signedFigure(wallet.periodResult, wallet.currency),
            gainPercentage = percentage(wallet.gainPercentage),
        )

        private fun monthOf(month: MonthSummaryResponse, period: ReportPeriod, currency: String): Month {
            val change = month.currentResultChange

            return Month(
                range = "${period.endDate.withDayOfMonth(1).format(DAY_AND_MONTH)} a ${period.endDate.format(FULL_DATE)}",
                contributions = if (month.contributions.signum() != 0) {
                    Figure(money(month.contributions, currency), TEXT_COLOR)
                } else {
                    Figure("Nenhum aporte", MUTED_COLOR)
                },
                realizedProfit = if (month.realizedProfit.signum() != 0) {
                    Figure(signedMoney(month.realizedProfit, currency), trendColor(month.realizedProfit))
                } else {
                    Figure("Nenhum resgate", MUTED_COLOR)
                },
                currentResultChange = change?.let { value -> Figure(signedMoney(value, currency), trendColor(value)) }
                    ?: Figure("Sem histórico", MUTED_COLOR),
                changeIconColor = change?.let(::trendColor) ?: MUTED_COLOR,
                bestMover = moverOf(
                    label = "Maior alta",
                    color = UP_COLOR,
                    background = UP_BACKGROUND,
                    mover = month.bestMover,
                    missingText = if (month.worstMover != null) "Nenhuma alta" else "Sem histórico",
                ),
                worstMover = moverOf(
                    label = "Maior queda",
                    color = DOWN_COLOR,
                    background = DOWN_BACKGROUND,
                    mover = month.worstMover,
                    missingText = if (month.bestMover != null) "Nenhuma queda" else "Sem histórico",
                ),
            )
        }

        private fun moverOf(label: String, color: String, background: String, mover: MoverResponse?, missingText: String): Mover =
            if (mover == null) {
                Mover(label, MUTED_COLOR, MISSING_BACKGROUND, missingText, null, Figure(MISSING_VALUE, MUTED_COLOR))
            } else {
                Mover(
                    label = label,
                    labelColor = color,
                    background = background,
                    name = mover.ticker ?: mover.name ?: MISSING_VALUE,
                    detail = mover.name.takeIf { mover.ticker != null },
                    value = Figure(signedPercentage(mover.changePct), trendColor(mover.changePct)),
                )
            }

        private fun explanationOf(report: PerformanceReport): String {
            val kind = report.period.kind
            val hasNewWallet = report.wallets.any { wallet -> wallet.newSince != null }
            val hasForeignWallet = report.wallets.any { wallet -> wallet.currency != report.displayCurrency }

            return buildString {
                append("\"${kind.columnLabel}\" soma o lucro realizado nas vendas e resgates ${kind.ofThePeriod} à variação do resultado atual das posições, ")
                append("então novos aportes não aparecem como desempenho e uma venda com lucro não aparece como perda. ")
                append("\"Valor atual\" inclui o caixa de cada carteira.")
                if (hasNewWallet) {
                    append(" Uma carteira criada durante ${kind.duringThePeriod} é comparada a partir do primeiro registro dela.")
                }
                append(" Os totais estão na sua moeda preferida (${report.displayCurrency})")
                append(if (hasForeignWallet) "; carteiras em outra moeda são convertidas pela taxa de Preços e Moedas." else ".")
            }
        }

        private fun signedFigure(value: BigDecimal, currency: String): Figure {
            val color = when (rounded(value).signum()) {
                1 -> UP_COLOR
                -1 -> DOWN_COLOR
                else -> TEXT_COLOR
            }

            return Figure(signedMoney(value, currency), color)
        }

        private fun trendColor(value: BigDecimal): String = if (value.signum() >= 0) UP_COLOR else DOWN_COLOR

        private fun signOf(value: BigDecimal, withPlus: Boolean): String = when (rounded(value).signum()) {
            1 -> if (withPlus) "+" else ""
            -1 -> MINUS
            else -> ""
        }

        private fun decimal(value: BigDecimal): String =
            DecimalFormat("#,##0.00", DecimalFormatSymbols(PORTUGUESE_BRAZIL)).format(rounded(value).abs())

        private fun rounded(value: BigDecimal): BigDecimal = value.setScale(2, RoundingMode.HALF_UP)

        private fun weekday(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.FULL, PORTUGUESE_BRAZIL)

        private fun monthName(date: LocalDate): String = date.month.getDisplayName(TextStyle.FULL, PORTUGUESE_BRAZIL)
    }
}
