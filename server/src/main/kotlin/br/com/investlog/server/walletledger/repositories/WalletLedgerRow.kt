package br.com.investlog.server.walletledger.repositories

import br.com.investlog.server.walletledger.rest.payloads.WalletLedgerEventType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class WalletLedgerRow(
    val id: UUID,
    val date: LocalDate,
    val createdAt: OffsetDateTime,
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
)
