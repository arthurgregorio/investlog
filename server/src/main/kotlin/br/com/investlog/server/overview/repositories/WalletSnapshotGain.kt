package br.com.investlog.server.overview.repositories

import java.math.BigDecimal
import java.time.LocalDate

data class WalletSnapshotGain(
    val walletId: Long,
    val snapshotDate: LocalDate,
    val gain: BigDecimal,
)
