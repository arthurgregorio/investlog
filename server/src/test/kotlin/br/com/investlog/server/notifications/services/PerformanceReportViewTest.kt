package br.com.investlog.server.notifications.services

import br.com.investlog.server.mail.MailTemplateConfiguration
import br.com.investlog.server.notifications.repositories.RealizedProfit
import br.com.investlog.server.notifications.repositories.ReportWalletSnapshot
import br.com.investlog.server.notifications.services.PerformanceReportView.Companion.DOWN_COLOR
import br.com.investlog.server.notifications.services.PerformanceReportView.Companion.MUTED_COLOR
import br.com.investlog.server.notifications.services.PerformanceReportView.Companion.TEXT_COLOR
import br.com.investlog.server.notifications.services.PerformanceReportView.Companion.UP_COLOR
import br.com.investlog.server.overview.rest.payloads.MonthSummaryResponse
import br.com.investlog.server.overview.rest.payloads.MoverResponse
import org.thymeleaf.context.Context
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PerformanceReportViewTest {

    private val monthEnd = ReportPeriod(PerformanceReportKind.MONTH_END, LocalDate.parse("2026-09-30"), LocalDate.parse("2026-10-31"))

    private val snapshots = listOf(
        snapshot(1, "Ações Brasil", "BRL", currentValue = "1000", totalInvested = "800", gain = "200", gainPercentage = "25", startGain = "150", cash = "50"),
        snapshot(2, "Ações EUA", "USD", currentValue = "100", totalInvested = "120", gain = "-20", gainPercentage = "-16.6667", startGain = "-10"),
        snapshot(3, "Previdência", "BRL", currentValue = "300", totalInvested = "300", gain = "0", gainPercentage = "0", startGain = "0", firstSnapshotDate = "2026-10-14"),
    )

    private val realizedProfits = listOf(
        RealizedProfit(1, LocalDate.parse("2026-09-30"), BigDecimal("99")),
        RealizedProfit(1, LocalDate.parse("2026-10-31"), BigDecimal("10")),
        RealizedProfit(2, LocalDate.parse("2026-10-15"), BigDecimal("5")),
        RealizedProfit(3, LocalDate.parse("2026-10-10"), BigDecimal("7")),
        RealizedProfit(3, LocalDate.parse("2026-10-20"), BigDecimal("3")),
    )

    private val rates = mapOf("BRL" to BigDecimal.ONE, "USD" to BigDecimal("5"))

    @Test
    fun `assembles each wallet in its own currency and the totals in the preferred one`() {
        val report = PerformanceReportService.assemble(monthEnd, "BRL", snapshots, realizedProfits, rates)
        val (brazil, unitedStates, pension) = report.wallets

        assertEquals(BigDecimal("1050"), brazil.currentValue)
        assertEquals(BigDecimal("10"), brazil.realizedProfit)
        assertEquals(BigDecimal("50"), brazil.currentResultChange)
        assertEquals(BigDecimal("60"), brazil.periodResult)
        assertNull(brazil.newSince)

        assertEquals(BigDecimal("5"), unitedStates.realizedProfit)
        assertEquals(BigDecimal("-5"), unitedStates.periodResult)

        assertEquals(BigDecimal("3"), pension.realizedProfit)
        assertEquals(LocalDate.parse("2026-10-14"), pension.newSince)

        assertEquals(0, BigDecimal("1850").compareTo(report.totalValue))
        assertEquals(0, BigDecimal("100").compareTo(report.totalCurrentResult))
        assertEquals(0, BigDecimal("38").compareTo(report.totalPeriodResult))
        assertEquals(0, BigDecimal.ZERO.compareTo(report.totalCurrentResultChange))
        assertEquals(BigDecimal("5.88"), report.totalGainPercentage!!.setScale(2, java.math.RoundingMode.HALF_UP))
    }

    @Test
    fun `formats the month-end report with the Neste mes block`() {
        val report = PerformanceReportService.assemble(monthEnd, "BRL", snapshots, realizedProfits, rates).copy(
            month = MonthSummaryResponse(
                month = "2026-10",
                contributions = BigDecimal("355"),
                realizedProfit = BigDecimal.ZERO,
                currentResultChange = BigDecimal.ZERO,
                bestMover = MoverResponse("STOCKS", "WEGE3", "WEG", BigDecimal("8.42")),
                worstMover = null,
            ),
        )

        val view = PerformanceReportView.of(report)

        assertEquals("Relatório de outubro de 2026", view.subject)
        assertEquals("Relatório de fim de mês", view.label)
        assertEquals("Outubro de 2026", view.title)
        assertEquals("Fechamento de 31/10/2026 comparado com o de 30/09/2026.", view.subtitle)
        assertEquals("Resultado no mês", view.resultLabel)
        assertEquals("No mês", view.columnLabel)
        assertEquals(PerformanceReportView.Figure("+R$ 38,00", UP_COLOR), view.periodResult)
        assertEquals("R$ 1.850,00", view.totalValue)
        assertEquals("R$ 100,00", view.currentResult)
        assertEquals("Você recebe o relatório de fim de mês todo dia 1º.", view.footer)

        assertEquals(
            listOf(
                PerformanceReportView.Row("Ações Brasil", null, "R$ 1.050,00", "R$ 200,00", PerformanceReportView.Figure("+R$ 60,00", UP_COLOR), "25,00%"),
                PerformanceReportView.Row("Ações EUA", "em USD", "US$ 100,00", "−US$ 20,00", PerformanceReportView.Figure("−US$ 5,00", DOWN_COLOR), "−16,67%"),
                PerformanceReportView.Row("Previdência", "nova, desde 14/10", "R$ 300,00", "R$ 0,00", PerformanceReportView.Figure("+R$ 3,00", UP_COLOR), "0,00%"),
            ),
            view.rows,
        )
        assertEquals(
            PerformanceReportView.Row("Total", "na sua moeda (BRL)", "R$ 1.850,00", "R$ 100,00", PerformanceReportView.Figure("+R$ 38,00", UP_COLOR), "5,88%"),
            view.total,
        )
        assertTrue(view.explanation.startsWith("\"No mês\" soma o lucro realizado nas vendas e resgates do mês"))
        assertTrue(view.explanation.contains("Uma carteira criada durante o mês é comparada a partir do primeiro registro dela."))
        assertTrue(view.explanation.endsWith("(BRL); carteiras em outra moeda são convertidas pela taxa de Preços e Moedas."))

        val month = view.month!!
        assertEquals("01/10 a 31/10/2026", month.range)
        assertEquals(PerformanceReportView.Figure("R$ 355,00", TEXT_COLOR), month.contributions)
        assertEquals(PerformanceReportView.Figure("Nenhum resgate", MUTED_COLOR), month.realizedProfit)
        assertEquals(PerformanceReportView.Figure("R$ 0,00", UP_COLOR), month.currentResultChange)
        assertEquals(UP_COLOR, month.changeIconColor)
        assertEquals("Maior alta", month.bestMover.label)
        assertEquals("WEGE3", month.bestMover.name)
        assertEquals("WEG", month.bestMover.detail)
        assertEquals(PerformanceReportView.Figure("+8,42%", UP_COLOR), month.bestMover.value)
        assertEquals("Maior queda", month.worstMover.label)
        assertEquals(MUTED_COLOR, month.worstMover.labelColor)
        assertEquals("Nenhuma queda", month.worstMover.name)
        assertEquals(PerformanceReportView.Figure("—", MUTED_COLOR), month.worstMover.value)

        val html = render(view)
        assertTrue(html.contains("No mês"))
        assertTrue(html.contains("01/10 a 31/10/2026"))
        assertTrue(html.contains("Aportes no mês"))
        assertTrue(html.contains("WEGE3"))
        assertTrue(html.contains("Nenhuma queda"))
        assertTrue(html.contains("nova, desde 14/10"))
        assertTrue(html.contains("−US$ 5,00"))
        assertTrue(html.contains("href=\"https://investlog.example.com/overview\""))
        assertTrue(html.contains("href=\"https://investlog.example.com/notifications\""))
        assertTrue(html.contains("Gerenciar notificações"))
    }

    @Test
    fun `the month block shows the card's missing texts and signs`() {
        val report = PerformanceReportService.assemble(monthEnd, "BRL", snapshots.take(1), emptyList(), rates).copy(
            month = MonthSummaryResponse(
                month = "2026-10",
                contributions = BigDecimal.ZERO,
                realizedProfit = BigDecimal("-4"),
                currentResultChange = null,
                bestMover = null,
                worstMover = null,
            ),
        )

        val month = PerformanceReportView.of(report).month!!

        assertEquals(PerformanceReportView.Figure("Nenhum aporte", MUTED_COLOR), month.contributions)
        assertEquals(PerformanceReportView.Figure("−R$ 4,00", DOWN_COLOR), month.realizedProfit)
        assertEquals(PerformanceReportView.Figure("Sem histórico", MUTED_COLOR), month.currentResultChange)
        assertEquals(MUTED_COLOR, month.changeIconColor)
        assertEquals("Sem histórico", month.bestMover.name)
        assertEquals("Sem histórico", month.worstMover.name)

        val movers = PerformanceReportView.of(
            report.copy(
                month = report.month!!.copy(
                    currentResultChange = BigDecimal("-3"),
                    bestMover = null,
                    worstMover = MoverResponse("CRYPTO", null, "Bitcoin", BigDecimal("-6.15")),
                ),
            ),
        ).month!!

        assertEquals(DOWN_COLOR, movers.changeIconColor)
        assertEquals("Nenhuma alta", movers.bestMover.name)
        assertEquals("Bitcoin", movers.worstMover.name)
        assertNull(movers.worstMover.detail)
        assertEquals(PerformanceReportView.Figure("−6,15%", DOWN_COLOR), movers.worstMover.value)

        val unnamed = PerformanceReportView.of(
            report.copy(month = report.month!!.copy(bestMover = MoverResponse("STOCKS", null, null, BigDecimal("1")))),
        ).month!!
        assertEquals("—", unnamed.bestMover.name)
    }

    @Test
    fun `periodic reports use their own header, column and footer`() {
        val single = snapshots.take(1)

        val daily = PerformanceReportView.of(
            PerformanceReportService.assemble(period(PerformanceReportKind.DAILY, "2026-10-30", "2026-11-02"), "BRL", single, emptyList(), rates)
        )
        assertEquals("Relatório diário · 02/11", daily.subject)
        assertEquals("Relatório diário", daily.label)
        assertEquals("Segunda-feira, 02/11/2026", daily.title)
        assertEquals("Comparado com o fechamento de sexta-feira, 30/10/2026.", daily.subtitle)
        assertEquals("Resultado no período", daily.resultLabel)
        assertEquals("No período", daily.columnLabel)
        assertEquals("Você recebe este relatório de terça a sábado porque escolheu a frequência diária.", daily.footer)
        assertNull(daily.month)
        assertFalse(daily.explanation.contains("criada"))
        assertTrue(daily.explanation.endsWith("Os totais estão na sua moeda preferida (BRL)."))

        val weekly = PerformanceReportView.of(
            PerformanceReportService.assemble(period(PerformanceReportKind.WEEKLY, "2026-10-25", "2026-11-01"), "BRL", single, emptyList(), rates)
        )
        assertEquals("Relatório semanal · 26/10 a 01/11", weekly.subject)
        assertEquals("Semana de 26/10 a 01/11", weekly.title)
        assertEquals("Fechamento de domingo, 01/11, comparado com o de 25/10.", weekly.subtitle)
        assertEquals("Na semana", weekly.columnLabel)
        assertTrue(weekly.explanation.contains("resgates da semana"))
        assertEquals("Você recebe este relatório toda segunda-feira porque escolheu a frequência semanal.", weekly.footer)

        val biweekly = PerformanceReportView.of(
            PerformanceReportService.assemble(period(PerformanceReportKind.BIWEEKLY, "2026-10-15", "2026-10-31"), "BRL", single, emptyList(), rates)
        )
        assertEquals("Relatório quinzenal · 16 a 31/10", biweekly.subject)
        assertEquals("16 a 31 de outubro de 2026", biweekly.title)
        assertEquals("Fechamento de 31/10 comparado com o de 15/10.", biweekly.subtitle)
        assertEquals("Na quinzena", biweekly.columnLabel)
        assertEquals("Você recebe este relatório nos dias 1º e 16 porque escolheu a frequência quinzenal.", biweekly.footer)

        val firstHalf = PerformanceReportView.of(
            PerformanceReportService.assemble(period(PerformanceReportKind.BIWEEKLY, "2026-10-31", "2026-11-15"), "BRL", single, emptyList(), rates)
        )
        assertEquals("Relatório quinzenal · 1 a 15/11", firstHalf.subject)
        assertEquals("1 a 15 de novembro de 2026", firstHalf.title)

        val html = render(daily)
        assertTrue(html.contains("Segunda-feira, 02/11/2026"))
        assertTrue(html.contains("No período"))
        assertFalse(html.contains("Aportes no mês"))
    }

    @Test
    fun `a wallet with nothing invested has no total gain and a flat period`() {
        val flat = snapshot(4, "Caixa", "BRL", currentValue = "0", totalInvested = "0", gain = "0", gainPercentage = "0", startGain = "0", cash = "25")
        val newWallet = snapshot(5, "Nova", "BRL", currentValue = "0", totalInvested = "0", gain = "0", gainPercentage = "0", startGain = "0", firstSnapshotDate = "2026-11-02")

        val view = PerformanceReportView.of(
            PerformanceReportService.assemble(period(PerformanceReportKind.DAILY, "2026-10-30", "2026-11-02"), "USD", listOf(flat, newWallet), emptyList(), rates)
        )

        assertEquals(PerformanceReportView.Figure("R$ 0,00", TEXT_COLOR), view.rows.first().periodResult)
        assertEquals("R$ 25,00", view.rows.first().currentValue)
        assertEquals("em BRL · nova, desde 02/11", view.rows.last().note)
        assertEquals("—", view.total.gainPercentage)
        assertEquals("US$ 5,00", view.totalValue)
        assertEquals("EUR 1.234,56", PerformanceReportView.money(BigDecimal("1234.555"), "EUR"))
        assertEquals("+12,35%", PerformanceReportView.signedPercentage(BigDecimal("12.345")))
        assertEquals("R$ 0,00", PerformanceReportView.signedMoney(BigDecimal("-0.004"), "BRL"))
    }

    private fun render(view: PerformanceReportView): String {
        val context = Context(Locale.forLanguageTag("pt-BR")).apply {
            setVariable("report", view)
            setVariable("subject", view.subject)
            setVariable("publicUrl", "https://investlog.example.com")
        }

        return MailTemplateConfiguration().mailTemplateEngine().process("performance-report", context)
    }

    private fun period(kind: PerformanceReportKind, startDate: String, endDate: String) =
        ReportPeriod(kind, LocalDate.parse(startDate), LocalDate.parse(endDate))

    private fun snapshot(
        walletId: Long,
        name: String,
        currency: String,
        currentValue: String,
        totalInvested: String,
        gain: String,
        gainPercentage: String,
        startGain: String,
        firstSnapshotDate: String? = null,
        cash: String = "0",
    ) = ReportWalletSnapshot(
        walletId = walletId,
        name = name,
        currency = currency,
        currentValue = BigDecimal(currentValue),
        totalInvested = BigDecimal(totalInvested),
        gain = BigDecimal(gain),
        gainPercentage = BigDecimal(gainPercentage),
        startGain = BigDecimal(startGain),
        firstSnapshotDate = firstSnapshotDate?.let(LocalDate::parse),
        cash = BigDecimal(cash),
    )
}
