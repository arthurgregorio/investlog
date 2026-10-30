package br.com.investlog.server.cash.repositories

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class CashWithdrawalRow(
    val id: Long,
    val externalId: UUID,
    val withdrawalDate: LocalDate,
    val amount: BigDecimal,
    val bankName: String?,
)
