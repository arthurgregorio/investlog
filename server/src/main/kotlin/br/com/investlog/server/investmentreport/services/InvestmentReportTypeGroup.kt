package br.com.investlog.server.investmentreport.services

data class InvestmentReportTypeGroup(
    val typeLabel: String?,
    val positions: List<InvestmentReportPosition>,
    val totals: InvestmentReportTotals,
) {
    val label: String = typeLabel ?: UNTYPED_LABEL

    companion object {
        const val UNTYPED_LABEL = "Outros"
    }
}
