package br.com.investlog.server.notifications

import br.com.investlog.server.jooq.finances.enums.WalletKind
import br.com.investlog.server.notifications.repositories.FrozenHolding
import br.com.investlog.server.notifications.repositories.FrozenHoldingAlert
import br.com.investlog.server.notifications.repositories.NotificationPreferences
import br.com.investlog.server.notifications.repositories.NotificationRecipient
import br.com.investlog.server.notifications.services.FrozenHoldingAlertLine
import br.com.investlog.server.notifications.services.FrozenHoldingAlertMail
import br.com.investlog.server.notifications.services.FrozenHoldingAlertService
import java.math.BigDecimal
import java.time.LocalTime
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FrozenHoldingAlertRulesTest {

    @Test
    fun `with the default 1 percent, 0,80 percent is not green and 1,20 percent is`() {
        val minimum = BigDecimal("1.00")

        assertFalse(holdingAt("0.80").isAboveMinimumGain(minimum))
        assertFalse(holdingAt("1.00").isAboveMinimumGain(minimum))
        assertTrue(holdingAt("1.20").isAboveMinimumGain(minimum))
    }

    @Test
    fun `with 0, any gain above zero is green`() {
        val minimum = BigDecimal("0.00")

        assertTrue(holdingAt("0.10").isAboveMinimumGain(minimum))
        assertFalse(holdingAt("0.00").isAboveMinimumGain(minimum))
        assertFalse(holdingAt("-0.50").isAboveMinimumGain(minimum))
    }

    @Test
    fun `with 5 percent, 3,20 percent is ignored and 5,40 percent is green`() {
        val minimum = BigDecimal("5.00")

        assertFalse(holdingAt("3.20").isAboveMinimumGain(minimum))
        assertTrue(holdingAt("5.40").isAboveMinimumGain(minimum))
    }

    @Test
    fun `a holding with no current value, gain or gain percentage is never green`() {
        val minimum = BigDecimal("0.00")

        assertFalse(holdingAt("2.00").copy(currentValue = null).isAboveMinimumGain(minimum))
        assertFalse(holdingAt("2.00").copy(gain = null).isAboveMinimumGain(minimum))
        assertFalse(holdingAt("2.00").copy(gainPercentage = null).isAboveMinimumGain(minimum))
        assertFalse(holdingAt("2.00").copy(gain = BigDecimal.ZERO).isAboveMinimumGain(minimum))
    }

    @Test
    fun `turning green starts a new alert, staying green keeps it and falling back clears green since`() {
        val firstCheck = OffsetDateTime.parse("2026-11-09T05:00:00Z")
        val secondCheck = OffsetDateTime.parse("2026-11-09T11:00:00Z")

        val turnedGreen = FrozenHoldingAlert.after(null, green = true, checkedAt = firstCheck)
        assertEquals(FrozenHoldingAlert(green = true, greenSince = firstCheck, alertedAt = null), turnedGreen)
        assertTrue(turnedGreen.isNew)

        val alerted = turnedGreen.alerted(secondCheck)
        assertFalse(alerted.isNew)
        assertSame(alerted, FrozenHoldingAlert.after(alerted, green = true, checkedAt = secondCheck))

        val fellBack = FrozenHoldingAlert.after(alerted, green = false, checkedAt = secondCheck)
        assertEquals(FrozenHoldingAlert(green = false, greenSince = null, alertedAt = secondCheck), fellBack)
        assertFalse(fellBack.isNew)

        val greenAgain = FrozenHoldingAlert.after(fellBack, green = true, checkedAt = secondCheck)
        assertEquals(FrozenHoldingAlert(green = true, greenSince = secondCheck, alertedAt = null), greenAgain)

        assertEquals(FrozenHoldingAlert(green = false, greenSince = null, alertedAt = null), FrozenHoldingAlert.after(null, green = false, checkedAt = firstCheck))
    }

    @Test
    fun `emails are sent only from 08,00 to 22,00`() {
        assertFalse(FrozenHoldingAlertService.isWithinSendingWindow(LocalTime.of(7, 59, 59)))
        assertTrue(FrozenHoldingAlertService.isWithinSendingWindow(LocalTime.of(8, 0)))
        assertTrue(FrozenHoldingAlertService.isWithinSendingWindow(LocalTime.of(14, 0, 0, 120_000_000)))
        assertTrue(FrozenHoldingAlertService.isWithinSendingWindow(LocalTime.of(22, 0, 0, 350_000_000)))
        assertFalse(FrozenHoldingAlertService.isWithinSendingWindow(LocalTime.of(22, 1)))
        assertFalse(FrozenHoldingAlertService.isWithinSendingWindow(LocalTime.of(0, 0)))
    }

    @Test
    fun `the title counts only the new holdings`() {
        assertEquals("1 investimento congelado passou do ganho mínimo", FrozenHoldingAlertMail.titleFor(1))
        assertEquals("3 investimentos congelados passaram do ganho mínimo", FrozenHoldingAlertMail.titleFor(3))
    }

    @Test
    fun `lines put the new holdings first, show a fund by name and date the earlier alerts in Sao Paulo`() {
        val alertedLateAtNight = OffsetDateTime.parse("2026-10-30T01:00:00Z")
        val fund = holdingAt("1.12").copy(
            kind = WalletKind.FUNDS,
            ticker = null,
            name = "CSHG Logística",
            walletName = "Fundos Imobiliários",
            currentValue = BigDecimal("8012.40"),
            gain = BigDecimal("88.50"),
            alert = FrozenHoldingAlert(green = true, greenSince = alertedLateAtNight, alertedAt = alertedLateAtNight),
        )
        val crypto = holdingAt("9.30").copy(
            kind = WalletKind.CRYPTO,
            ticker = "SOL",
            name = "Solana",
            walletName = "Cripto",
            walletCurrency = "USD",
            currentValue = BigDecimal("14582.1"),
            gain = BigDecimal("1240.774"),
            alert = FrozenHoldingAlert(green = true, greenSince = alertedLateAtNight, alertedAt = null),
        )

        val lines = FrozenHoldingAlertMail.linesFor(listOf(fund, crypto))

        assertEquals(
            listOf(
                FrozenHoldingAlertLine(
                    title = "SOL",
                    subtitle = "Cripto · Solana",
                    currentValue = "US$ 14.582,10",
                    gain = "+US$ 1.240,77",
                    gainPercentage = "+9,30%",
                    newSinceLastAlert = true,
                ),
                FrozenHoldingAlertLine(
                    title = "CSHG Logística",
                    subtitle = "Fundos Imobiliários · Fundo · avisado em 29/10",
                    currentValue = "R$ 8.012,40",
                    gain = "+R$ 88,50",
                    gainPercentage = "+1,12%",
                    newSinceLastAlert = false,
                ),
            ),
            lines,
        )
    }

    @Test
    fun `the message goes to the recipient with the threshold and the lines`() {
        val recipient = NotificationRecipient(
            id = 1,
            name = "Ana",
            email = "ana@example.com",
            preferredCurrency = "BRL",
            preferences = NotificationPreferences.DEFAULTS.copy(frozenAlertMinimumGainPercentage = BigDecimal("12.5")),
        )
        val holding = holdingAt("13.00").copy(alert = FrozenHoldingAlert(green = true, greenSince = null, alertedAt = null))

        val message = FrozenHoldingAlertMail.messageFor(recipient, listOf(holding))

        assertEquals(listOf("ana@example.com"), message.to)
        assertEquals("1 investimento congelado passou do ganho mínimo", message.subject)
        assertEquals(FrozenHoldingAlertMail.TEMPLATE_NAME, message.templateName)
        assertEquals(true, message.variables["singular"])
        assertEquals("12,50%", message.variables["minimumGainPercentage"])
        assertEquals(FrozenHoldingAlertMail.linesFor(listOf(holding)), message.variables["lines"])
    }

    private fun holdingAt(gainPercentage: String): FrozenHolding {
        val percentage = BigDecimal(gainPercentage)

        return FrozenHolding(
            kind = WalletKind.STOCKS,
            externalId = UUID.randomUUID(),
            ticker = "MGLU3",
            name = "Magazine Luiza",
            walletName = "Ações Brasil",
            walletCurrency = "BRL",
            currentValue = BigDecimal("1000").add(BigDecimal("10").multiply(percentage)),
            gain = BigDecimal("10").multiply(percentage),
            gainPercentage = percentage,
            alert = null,
        )
    }
}
