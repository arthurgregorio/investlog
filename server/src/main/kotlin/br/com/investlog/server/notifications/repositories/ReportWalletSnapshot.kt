package br.com.investlog.server.notifications.repositories

import java.math.BigDecimal
import java.time.LocalDate

data class ReportWalletSnapshot(
    val walletId: Long,
    val name: String,
    val currency: String,
    val currentValue: BigDecimal,
    val totalInvested: BigDecimal,
    val gain: BigDecimal,
    val gainPercentage: BigDecimal,
    val startGain: BigDecimal,
    val firstSnapshotDate: LocalDate?,
    val cash: BigDecimal,
)
