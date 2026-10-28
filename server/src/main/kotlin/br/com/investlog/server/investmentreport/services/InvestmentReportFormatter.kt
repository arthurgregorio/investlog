package br.com.investlog.server.investmentreport.services

import br.com.investlog.server.wallets.rest.payloads.WalletKind
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

class InvestmentReportFormatter(private val displayCurrency: String) {

    private val symbols = DecimalFormatSymbols.getInstance(REPORT_LOCALE).apply { minusSign = MINUS_SIGN }

    private val moneyFormat = decimalFormat("#,##0.00")
    private val signedMoneyFormat = decimalFormat("+#,##0.00;${MINUS_SIGN}#,##0.00")
    private val shareFormat = decimalFormat("#,##0.0'%'")
    private val signedPercentageFormat = decimalFormat("+#,##0.0'%';${MINUS_SIGN}#,##0.0'%'")
    private val quantityFormat = decimalFormat("#,##0.##")
    private val cryptoQuantityFormat = decimalFormat("#,##0.####")
    private val rateFormat = decimalFormat("#,##0.00")

    fun money(value: BigDecimal?): String = value?.let { moneyFormat.format(it) } ?: EMPTY_VALUE

    fun signedMoney(value: BigDecimal?): String = value?.let { signedMoneyFormat.format(it) } ?: EMPTY_VALUE

    fun signedPercentage(value: BigDecimal?): String = value?.let { signedPercentageFormat.format(it) } ?: EMPTY_VALUE

    fun share(value: BigDecimal?): String = value?.let { shareFormat.format(it) } ?: EMPTY_VALUE

    fun quantity(value: BigDecimal?, kind: WalletKind): String {
        val format = if (kind == WalletKind.CRYPTO) cryptoQuantityFormat else quantityFormat
        return value?.let { format.format(it) } ?: EMPTY_VALUE
    }

    fun tone(value: BigDecimal?): String = when {
        value == null -> ""
        value.signum() < 0 -> "negative"
        else -> "positive"
    }

    fun positionCount(count: Int): String = if (count == 1) "1 posição" else "$count posições"

    fun kindLabel(kind: WalletKind): String = when (kind) {
        WalletKind.STOCKS -> "Ações"
        WalletKind.CRYPTO -> "Cripto"
        WalletKind.FUNDS -> "Fundos"
    }

    fun kindColor(kind: WalletKind): String = when (kind) {
        WalletKind.STOCKS -> "#2b6cb0"
        WalletKind.CRYPTO -> "#e0900a"
        WalletKind.FUNDS -> "#0ca678"
    }

    fun currencySymbol(): String = Currency.getInstance(displayCurrency).getSymbol(REPORT_LOCALE)

    fun currencyDescription(): String =
        "$displayCurrency — ${Currency.getInstance(displayCurrency).getDisplayName(REPORT_LOCALE)}"

    fun exchangeRate(exchangeRate: InvestmentReportExchangeRate): String =
        "1 ${exchangeRate.currency} = ${currencySymbol()} ${rateFormat.format(exchangeRate.rate)}"

    fun date(dateTime: ZonedDateTime): String = dateTime.format(DATE_FORMAT)

    fun time(dateTime: ZonedDateTime): String = dateTime.format(TIME_FORMAT)

    private fun decimalFormat(pattern: String): DecimalFormat =
        DecimalFormat(pattern, symbols).apply { roundingMode = RoundingMode.HALF_UP }

    companion object {
        private val REPORT_LOCALE: Locale = Locale.forLanguageTag("pt-BR")
        private const val MINUS_SIGN = '−'
        private const val EMPTY_VALUE = "—"
        private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH'h'mm")
    }
}
