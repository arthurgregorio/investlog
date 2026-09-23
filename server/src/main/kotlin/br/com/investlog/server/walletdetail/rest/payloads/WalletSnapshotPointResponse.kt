package br.com.investlog.server.walletdetail.rest.payloads

import java.math.BigDecimal
import java.time.LocalDate

data class WalletSnapshotPointResponse(
    val snapshotDate: LocalDate,
    val currentValue: BigDecimal,
    val totalInvested: BigDecimal,
    val gain: BigDecimal,
    val gainPct: BigDecimal,
)
