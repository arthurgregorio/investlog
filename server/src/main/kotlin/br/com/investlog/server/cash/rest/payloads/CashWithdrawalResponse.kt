package br.com.investlog.server.cash.rest.payloads

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class CashWithdrawalResponse(
    val id: UUID,
    val walletId: UUID,
    val withdrawalDate: LocalDate,
    val amount: BigDecimal,
    val bankName: String?,
)
