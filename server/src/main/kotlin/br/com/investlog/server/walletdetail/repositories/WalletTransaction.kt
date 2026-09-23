package br.com.investlog.server.walletdetail.repositories

import java.math.BigDecimal
import java.time.LocalDate

data class WalletTransaction(
    val transactionDate: LocalDate,
    val investmentName: String,
    val amount: BigDecimal,
)
