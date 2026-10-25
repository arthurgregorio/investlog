package br.com.investlog.server.cash.repositories

import java.time.LocalDate
import java.util.UUID

data class FundedPurchase(
    val eventId: UUID,
    val purchaseDate: LocalDate,
    val holdingName: String,
    val ticker: String?,
)
