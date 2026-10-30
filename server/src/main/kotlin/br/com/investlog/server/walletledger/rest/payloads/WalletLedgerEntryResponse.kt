package br.com.investlog.server.walletledger.rest.payloads

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class WalletLedgerEntryResponse(
    val id: UUID,
    val date: LocalDate,
    val type: WalletLedgerEventType,
    val kind: String,
    val holdingId: UUID?,
    val holdingName: String?,
    val ticker: String?,
    val quantity: BigDecimal?,
    val amount: BigDecimal?,
    val profit: BigDecimal?,
    val counterpartName: String?,
    val originalAmount: BigDecimal?,
    val originalCurrency: String?,
    val rate: BigDecimal?,
    val cashUsed: BigDecimal?,
    val newMoney: BigDecimal?,
    val cashBalance: BigDecimal,
)
