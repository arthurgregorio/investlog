package br.com.investlog.server.cash.rest.payloads

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class CashTransferResponse(
    val id: UUID,
    val originWalletId: UUID,
    val destinationWalletId: UUID,
    val transferDate: LocalDate,
    val amount: BigDecimal,
    val rate: BigDecimal,
    val destinationAmount: BigDecimal,
)
