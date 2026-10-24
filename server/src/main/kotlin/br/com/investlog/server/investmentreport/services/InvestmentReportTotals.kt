package br.com.investlog.server.investmentreport.services

import java.math.BigDecimal

data class InvestmentReportTotals(
    val positionCount: Int,
    val costBasis: BigDecimal,
    val currentValue: BigDecimal,
    val result: BigDecimal,
    val resultPercentage: BigDecimal?,
    val share: BigDecimal?,
)
