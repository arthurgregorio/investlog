package br.com.investlog.server.walletdetail.rest.payloads

import java.math.BigDecimal
import java.time.LocalDate

data class WalletActivityResponse(
    val lastTransactionDate: LocalDate?,
    val lastTransactionName: String?,
    val lastTransactionAmount: BigDecimal?,
    val transactionCount: Int,
    val walletAgeInDays: Long?,
    val investmentCount: Int,
)
