package br.com.investlog.server.investmentreport.services

import java.time.ZonedDateTime

data class InvestmentReport(
    val holderName: String,
    val displayCurrency: String,
    val exchangeRates: List<InvestmentReportExchangeRate>,
    val generatedAt: ZonedDateTime,
    val sections: List<InvestmentReportSection>,
    val totals: InvestmentReportTotals,
)
