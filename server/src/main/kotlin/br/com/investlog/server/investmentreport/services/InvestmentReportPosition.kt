package br.com.investlog.server.investmentreport.services

import java.math.BigDecimal

data class InvestmentReportPosition(
    val ticker: String?,
    val name: String,
    val segment: String?,
    val quantity: BigDecimal?,
    val averagePrice: BigDecimal?,
    val currentPrice: BigDecimal?,
    val costBasis: BigDecimal,
    val currentValue: BigDecimal?,
    val result: BigDecimal?,
    val resultPercentage: BigDecimal?,
    val frozen: Boolean,
)
