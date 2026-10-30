package br.com.investlog.server.cash.repositories

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class CashTransferRow(
    val id: Long,
    val externalId: UUID,
    val originWalletId: Long,
    val destinationWalletId: Long,
    val transferDate: LocalDate,
    val amount: BigDecimal,
    val rate: BigDecimal,
    val destinationAmount: BigDecimal,
)
