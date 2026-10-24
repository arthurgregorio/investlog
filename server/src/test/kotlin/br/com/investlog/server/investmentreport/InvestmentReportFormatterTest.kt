package br.com.investlog.server.investmentreport

import br.com.investlog.server.investmentreport.services.InvestmentReportExchangeRate
import br.com.investlog.server.investmentreport.services.InvestmentReportFormatter
import br.com.investlog.server.wallets.rest.payloads.WalletKind
import java.math.BigDecimal
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class InvestmentReportFormatterTest {

    private val formatter = InvestmentReportFormatter("BRL")

    @Test
    fun `formats money in pt-BR with a true minus sign`() {
        assertEquals("1.234.567,89", formatter.money(BigDecimal("1234567.891")))
        assertEquals("−70,20", formatter.money(BigDecimal("-70.2")))
        assertEquals("0,01", formatter.money(BigDecimal("0.005")))
        assertEquals("—", formatter.money(null))
    }

    @Test
    fun `signs results explicitly`() {
        assertEquals("+164.494,08", formatter.signedMoney(BigDecimal("164494.08")))
        assertEquals("−24.177,49", formatter.signedMoney(BigDecimal("-24177.49")))
        assertEquals("+0,00", formatter.signedMoney(BigDecimal.ZERO))
        assertEquals("—", formatter.signedMoney(null))
        assertEquals("+14,7%", formatter.signedPercentage(BigDecimal("14.66")))
        assertEquals("−0,3%", formatter.signedPercentage(BigDecimal("-0.3016")))
        assertEquals("—", formatter.signedPercentage(null))
    }

    @Test
    fun `formats shares with one decimal`() {
        assertEquals("72,3%", formatter.share(BigDecimal("72.2999")))
        assertEquals("100,0%", formatter.share(BigDecimal("100")))
        assertEquals("—", formatter.share(null))
    }

    @Test
    fun `formats quantities without trailing zeros`() {
        assertEquals("0,173", formatter.quantity(BigDecimal("0.17300000")))
        assertEquals("1.500", formatter.quantity(BigDecimal("1500.00000000")))
        assertEquals("0,00012345", formatter.quantity(BigDecimal("0.00012345")))
        assertEquals("—", formatter.quantity(null))
    }

    @Test
    fun `tones results by their sign`() {
        assertEquals("positive", formatter.tone(BigDecimal.ZERO))
        assertEquals("positive", formatter.tone(BigDecimal.TEN))
        assertEquals("negative", formatter.tone(BigDecimal("-0.01")))
        assertEquals("", formatter.tone(null))
    }

    @Test
    fun `labels counts, classes and their colours`() {
        assertEquals("1 posição", formatter.positionCount(1))
        assertEquals("29 posições", formatter.positionCount(29))
        assertEquals(listOf("Ações", "Cripto", "Fundos"), WalletKind.entries.map { kind -> formatter.kindLabel(kind) })
        assertEquals(listOf("#2b6cb0", "#e0900a", "#0ca678"), WalletKind.entries.map { kind -> formatter.kindColor(kind) })
    }

    @Test
    fun `describes the display currency and the other currencies' rates`() {
        assertEquals("R$", formatter.currencySymbol())
        assertEquals("BRL — Real brasileiro", formatter.currencyDescription())
        assertEquals("1 USD = R$ 5,18", formatter.exchangeRate(InvestmentReportExchangeRate("USD", BigDecimal("5.1789"))))

        val dollarFormatter = InvestmentReportFormatter("USD")

        assertEquals("US$", dollarFormatter.currencySymbol())
        assertEquals("1 BRL = US$ 0,19", dollarFormatter.exchangeRate(InvestmentReportExchangeRate("BRL", BigDecimal("0.193"))))
    }

    @Test
    fun `formats the generation date and time`() {
        val generatedAt = ZonedDateTime.parse("2026-10-02T09:05:00-03:00[America/Sao_Paulo]")

        assertEquals("02/10/2026", formatter.date(generatedAt))
        assertEquals("09h05", formatter.time(generatedAt))
    }
}
