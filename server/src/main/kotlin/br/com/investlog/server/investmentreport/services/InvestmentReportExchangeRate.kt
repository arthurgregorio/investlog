package br.com.investlog.server.investmentreport.services

import java.math.BigDecimal

data class InvestmentReportExchangeRate(
    val currency: String,
    val rate: BigDecimal,
)
