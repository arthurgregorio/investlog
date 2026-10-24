package br.com.investlog.server.investmentreport.services

import br.com.investlog.server.wallets.rest.payloads.WalletKind

data class InvestmentReportSection(
    val kind: WalletKind,
    val typeGroups: List<InvestmentReportTypeGroup>,
    val totals: InvestmentReportTotals,
) {
    val showsTypeGroups: Boolean = typeGroups.any { typeGroup -> typeGroup.typeLabel != null }
}
