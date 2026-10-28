package br.com.investlog.server.investmentreport.services

import br.com.investlog.server.currencyrates.repositories.CurrencyRateRepository
import br.com.investlog.server.holdingsoverview.repositories.HoldingsOverviewRepository
import br.com.investlog.server.holdingsoverview.rest.payloads.HoldingRowResponse
import br.com.investlog.server.shared.security.CurrentUser
import br.com.investlog.server.wallets.rest.payloads.WalletKind
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Collator
import java.time.Clock
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

@Service
@Transactional(readOnly = true)
class InvestmentReportService(
    private val holdingsOverviewRepository: HoldingsOverviewRepository,
    private val currencyRateRepository: CurrencyRateRepository,
    private val clock: Clock,
) {

    fun build(user: CurrentUser, filters: InvestmentReportFilters): InvestmentReport? {
        val rows = holdingsOverviewRepository.findAllForReport(
            user.id, filters.kind?.jooqWalletKind, filters.typeLabels, filters.walletIds, filters.search, filters.hideFrozen
        )

        if (rows.isEmpty()) {
            return null
        }

        val currencies = rows.map { row -> row.walletCurrency }.toSet() + user.preferredCurrency
        val ratesByCurrency = currencies.associateWith { currency -> currencyRateRepository.findRateOrAnchor(currency) }

        return consolidate(rows, ratesByCurrency, user.preferredCurrency, user.name, ZonedDateTime.now(clock).withZoneSameInstant(REPORT_ZONE))
    }

    companion object {

        val REPORT_ZONE: ZoneId = ZoneId.of("America/Sao_Paulo")

        private const val CALCULATION_SCALE = 20
        private val ONE_HUNDRED = BigDecimal("100")

        fun consolidate(
            rows: List<HoldingRowResponse>,
            ratesByCurrency: Map<String, BigDecimal>,
            displayCurrency: String,
            holderName: String,
            generatedAt: ZonedDateTime,
        ): InvestmentReport {
            val displayRate = rateOf(ratesByCurrency, displayCurrency)

            val positionsByKind = rows
                .map { row -> row.convertedBy(rateOf(ratesByCurrency, row.walletCurrency).divide(displayRate, CALCULATION_SCALE, RoundingMode.HALF_UP)) }
                .groupBy { row -> row.mergeKey() }
                .values
                .map { mergedRows -> MergedPosition(mergedRows.first().kind, mergedRows.first().typeLabel, merge(mergedRows)) }
                .groupBy { mergedPosition -> mergedPosition.kind }

            val reportCurrentValue = positionsByKind.values.flatten().sumOf { mergedPosition -> mergedPosition.position.currentValue ?: BigDecimal.ZERO }

            val sections = WalletKind.entries.mapNotNull { kind ->
                positionsByKind[kind.text]?.let { mergedPositions -> section(kind, mergedPositions, reportCurrentValue) }
            }

            val exchangeRates = rows
                .map { row -> row.walletCurrency }
                .distinct()
                .filter { currency -> currency != displayCurrency }
                .sorted()
                .map { currency -> InvestmentReportExchangeRate(currency, rateOf(ratesByCurrency, currency).divide(displayRate, CALCULATION_SCALE, RoundingMode.HALF_UP)) }

            return InvestmentReport(
                holderName = holderName,
                displayCurrency = displayCurrency,
                exchangeRates = exchangeRates,
                generatedAt = generatedAt,
                sections = sections,
                totals = totals(sections.flatMap { section -> section.typeGroups.flatMap { typeGroup -> typeGroup.positions } }, reportCurrentValue),
            )
        }

        private fun rateOf(ratesByCurrency: Map<String, BigDecimal>, currency: String): BigDecimal =
            ratesByCurrency[currency] ?: BigDecimal.ONE

        private fun HoldingRowResponse.convertedBy(factor: BigDecimal): HoldingRowResponse = copy(
            costBasis = costBasis.multiply(factor),
            currentValue = currentValue?.multiply(factor),
        )

        private fun HoldingRowResponse.mergeKey(): MergeKey {
            val identity = if (kind == WalletKind.FUNDS.text) name else (ticker ?: name).uppercase()
            return MergeKey(kind, typeLabel, identity)
        }

        private fun merge(rows: List<HoldingRowResponse>): InvestmentReportPosition {
            val quantities = rows.mapNotNull { row -> row.quantity }
            val quantity = if (quantities.isEmpty()) null else quantities.fold(BigDecimal.ZERO, BigDecimal::add)
            val costBasis = rows.sumOf { row -> row.costBasis }
            val currentValue = if (rows.all { row -> row.currentValue == null }) null else rows.sumOf { row -> row.currentValue ?: BigDecimal.ZERO }
            val result = currentValue?.subtract(costBasis)

            return InvestmentReportPosition(
                ticker = rows.firstNotNullOfOrNull { row -> row.ticker }?.uppercase(),
                name = rows.map { row -> row.name }.firstOrNull { name -> name.isNotBlank() } ?: rows.first().name,
                segment = rows.firstNotNullOfOrNull { row -> row.segmentLabel },
                quantity = quantity,
                averagePrice = unitPriceOf(costBasis, quantity),
                currentPrice = currentValue?.let { unitPriceOf(it, quantity) },
                costBasis = costBasis,
                currentValue = currentValue,
                result = result,
                resultPercentage = result?.let { percentageOf(it, costBasis) },
                frozen = rows.any { row -> row.frozen },
            )
        }

        private fun unitPriceOf(amount: BigDecimal, quantity: BigDecimal?): BigDecimal? =
            if (quantity != null && quantity.signum() != 0) amount.divide(quantity, CALCULATION_SCALE, RoundingMode.HALF_UP) else null

        private fun section(kind: WalletKind, mergedPositions: List<MergedPosition>, reportCurrentValue: BigDecimal): InvestmentReportSection {
            val collator = Collator.getInstance(Locale.forLanguageTag("pt-BR"))

            val typeGroups = mergedPositions
                .groupBy { mergedPosition -> mergedPosition.typeLabel }
                .map { (typeLabel, typePositions) ->
                    val positions = typePositions
                        .map { mergedPosition -> mergedPosition.position }
                        .sortedWith(compareBy(collator) { position -> sortKey(kind, position) })
                    InvestmentReportTypeGroup(typeLabel, positions, totals(positions, reportCurrentValue))
                }
                .sortedWith(compareBy(collator) { typeGroup -> typeGroup.label })

            return InvestmentReportSection(
                kind = kind,
                typeGroups = typeGroups,
                totals = totals(typeGroups.flatMap { typeGroup -> typeGroup.positions }, reportCurrentValue),
            )
        }

        private fun sortKey(kind: WalletKind, position: InvestmentReportPosition): String =
            if (kind == WalletKind.FUNDS) position.name else position.ticker ?: position.name

        private fun totals(positions: List<InvestmentReportPosition>, reportCurrentValue: BigDecimal): InvestmentReportTotals {
            val costBasis = positions.sumOf { position -> position.costBasis }
            val currentValue = positions.sumOf { position -> position.currentValue ?: BigDecimal.ZERO }
            val result = currentValue.subtract(costBasis)

            return InvestmentReportTotals(
                positionCount = positions.size,
                costBasis = costBasis,
                currentValue = currentValue,
                result = result,
                resultPercentage = percentageOf(result, costBasis),
                share = percentageOf(currentValue, reportCurrentValue),
            )
        }

        private fun percentageOf(part: BigDecimal, whole: BigDecimal): BigDecimal? =
            if (whole.signum() != 0) part.divide(whole, CALCULATION_SCALE, RoundingMode.HALF_UP).multiply(ONE_HUNDRED) else null
    }

    private data class MergeKey(val kind: String, val typeLabel: String?, val identity: String)

    private data class MergedPosition(val kind: String, val typeLabel: String?, val position: InvestmentReportPosition)
}
