package br.com.investlog.server.notifications.services

import br.com.investlog.server.currencyrates.repositories.CurrencyRateRepository
import br.com.investlog.server.notifications.repositories.NotificationRecipient
import br.com.investlog.server.notifications.repositories.PerformanceReportRepository
import br.com.investlog.server.notifications.repositories.RealizedProfit
import br.com.investlog.server.notifications.repositories.ReportWalletSnapshot
import br.com.investlog.server.overview.services.OverviewService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode

@Service
@Transactional(readOnly = true)
class PerformanceReportService(
    private val performanceReportRepository: PerformanceReportRepository,
    private val currencyRateRepository: CurrencyRateRepository,
    private val overviewService: OverviewService,
) {

    fun build(recipient: NotificationRecipient, period: ReportPeriod): PerformanceReport? {
        val snapshots = performanceReportRepository.findWalletSnapshots(recipient.id, period.startDate, period.endDate)

        if (snapshots.isEmpty()) {
            return null
        }

        val currencies = snapshots.map { snapshot -> snapshot.currency }.toSet() + recipient.preferredCurrency
        val ratesByCurrency = currencies.associateWith { currency -> currencyRateRepository.findRateOrAnchor(currency) }
        val realizedProfits = performanceReportRepository.findRealizedProfits(recipient.id, period.startDate, period.endDate)
        val report = assemble(period, recipient.preferredCurrency, snapshots, realizedProfits, ratesByCurrency)

        if (period.kind != PerformanceReportKind.MONTH_END) {
            return report
        }

        return report.copy(
            month = overviewService.findClosedMonth(recipient.id, recipient.preferredCurrency, period.endDate, report.totalCurrentResultChange),
        )
    }

    companion object {

        private const val CALCULATION_SCALE = 20
        private const val PERCENTAGE_SCALE = 10
        private val ONE_HUNDRED = BigDecimal("100")

        fun assemble(
            period: ReportPeriod,
            displayCurrency: String,
            snapshots: List<ReportWalletSnapshot>,
            realizedProfits: List<RealizedProfit>,
            ratesByCurrency: Map<String, BigDecimal>,
        ): PerformanceReport {
            val displayRate = ratesByCurrency.getValue(displayCurrency)
            val conversionByCurrency = ratesByCurrency.mapValues { (_, rate) -> rate.divide(displayRate, CALCULATION_SCALE, RoundingMode.HALF_UP) }
            val wallets = snapshots.map { snapshot -> walletOf(snapshot, period, realizedProfits) }

            fun total(amountOf: (PerformanceReportWallet) -> BigDecimal): BigDecimal =
                wallets.fold(BigDecimal.ZERO) { total, wallet -> total + amountOf(wallet) * conversionByCurrency.getValue(wallet.currency) }

            val totalCurrentResult = total { wallet -> wallet.currentResult }
            val totalInvested = total { wallet -> wallet.totalInvested }

            return PerformanceReport(
                period = period,
                displayCurrency = displayCurrency,
                wallets = wallets,
                totalValue = total { wallet -> wallet.currentValue },
                totalCurrentResult = totalCurrentResult,
                totalPeriodResult = total { wallet -> wallet.periodResult },
                totalCurrentResultChange = total { wallet -> wallet.currentResultChange },
                totalGainPercentage = totalInvested.takeIf { invested -> invested.signum() != 0 }?.let { invested ->
                    totalCurrentResult.divide(invested, PERCENTAGE_SCALE, RoundingMode.HALF_UP).multiply(ONE_HUNDRED)
                },
            )
        }

        private fun walletOf(snapshot: ReportWalletSnapshot, period: ReportPeriod, realizedProfits: List<RealizedProfit>): PerformanceReportWallet {
            val realizedAfter = snapshot.firstSnapshotDate ?: period.startDate
            val realizedProfit = realizedProfits
                .filter { realized -> realized.walletId == snapshot.walletId && realized.resultDate.isAfter(realizedAfter) }
                .fold(BigDecimal.ZERO) { total, realized -> total + realized.profit }
            val currentResultChange = snapshot.gain - snapshot.startGain

            return PerformanceReportWallet(
                name = snapshot.name,
                currency = snapshot.currency,
                currentValue = snapshot.currentValue + snapshot.cash,
                totalInvested = snapshot.totalInvested,
                currentResult = snapshot.gain,
                realizedProfit = realizedProfit,
                currentResultChange = currentResultChange,
                periodResult = realizedProfit + currentResultChange,
                gainPercentage = snapshot.gainPercentage,
                newSince = snapshot.firstSnapshotDate,
            )
        }
    }
}
