package br.com.investlog.server.investmentreport.services

import br.com.investlog.server.wallets.rest.payloads.WalletKind
import java.util.UUID

data class InvestmentReportFilters(
    val kind: WalletKind?,
    val typeLabels: List<String>?,
    val walletIds: List<UUID>?,
    val search: String?,
    val hideFrozen: Boolean,
)
