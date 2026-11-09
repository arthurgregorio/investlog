package br.com.investlog.server.notifications.services

import br.com.investlog.server.jooq.finances.enums.WalletKind
import br.com.investlog.server.mail.services.MailMessage
import br.com.investlog.server.notifications.repositories.FrozenHolding
import br.com.investlog.server.notifications.repositories.NotificationRecipient
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

object FrozenHoldingAlertMail {

    const val TEMPLATE_NAME = "frozen-holdings-positive"

    private val PORTUGUESE_BRAZIL: Locale = Locale.forLanguageTag("pt-BR")
    private val SAO_PAULO: ZoneId = ZoneId.of("America/Sao_Paulo")
    private val ALERT_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")

    fun messageFor(recipient: NotificationRecipient, greenHoldings: List<FrozenHolding>): MailMessage {
        val newCount = greenHoldings.count { holding -> holding.alert?.isNew == true }
        val title = titleFor(newCount)

        return MailMessage(
            to = listOf(recipient.email),
            subject = title,
            templateName = TEMPLATE_NAME,
            variables = mapOf(
                "title" to title,
                "singular" to (newCount == 1),
                "minimumGainPercentage" to percentage(recipient.preferences.frozenAlertMinimumGainPercentage),
                "lines" to linesFor(greenHoldings),
            ),
        )
    }

    fun titleFor(newCount: Int): String =
        if (newCount == 1) {
            "1 investimento congelado passou do ganho mínimo"
        } else {
            "$newCount investimentos congelados passaram do ganho mínimo"
        }

    fun linesFor(greenHoldings: List<FrozenHolding>): List<FrozenHoldingAlertLine> =
        greenHoldings
            .sortedWith(compareByDescending<FrozenHolding> { holding -> holding.alert?.isNew == true }.thenBy(::titleOf))
            .map(::lineFor)

    private fun lineFor(holding: FrozenHolding): FrozenHoldingAlertLine {
        val newSinceLastAlert = holding.alert?.isNew == true
        val description = if (holding.kind == WalletKind.FUNDS) "Fundo" else holding.name
        val alertedOn = holding.alert?.alertedAt
            ?.takeUnless { newSinceLastAlert }
            ?.let { alertedAt -> " · avisado em ${alertedAt.atZoneSameInstant(SAO_PAULO).format(ALERT_DATE_FORMAT)}" }
            .orEmpty()

        return FrozenHoldingAlertLine(
            title = titleOf(holding),
            subtitle = "${holding.walletName} · $description$alertedOn",
            currentValue = money(holding.walletCurrency, holding.currentValue!!),
            gain = "+${money(holding.walletCurrency, holding.gain!!)}",
            gainPercentage = "+${percentage(holding.gainPercentage!!)}",
            newSinceLastAlert = newSinceLastAlert,
        )
    }

    private fun titleOf(holding: FrozenHolding): String = holding.ticker ?: holding.name

    private fun money(currency: String, amount: BigDecimal): String =
        "${Currency.getInstance(currency).getSymbol(PORTUGUESE_BRAZIL)} ${decimalFormat().format(amount)}"

    private fun percentage(value: BigDecimal): String = "${decimalFormat().format(value)}%"

    private fun decimalFormat(): DecimalFormat =
        DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(PORTUGUESE_BRAZIL)).apply { roundingMode = RoundingMode.HALF_UP }
}
