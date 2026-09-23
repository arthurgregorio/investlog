package br.com.investlog.server.walletdetail.rest.payloads

import java.math.BigDecimal
import java.util.UUID

data class WalletDetailResponse(
    val id: UUID,
    val name: String,
    val kind: String,
    val currency: String,
    val currentValue: BigDecimal,
    val totalInvested: BigDecimal,
    val gain: BigDecimal,
    val gainPct: BigDecimal?,
    val series: List<WalletSnapshotPointResponse>,
    val dayChange: BigDecimal?,
    val weekChange: BigDecimal?,
    val monthChange: BigDecimal?,
    val bestPerformer: WalletPerformerResponse?,
    val worstPerformer: WalletPerformerResponse?,
    val largestHoldingName: String?,
    val largestHoldingShare: BigDecimal?,
    val activity: WalletActivityResponse,
)
