package br.com.investlog.server.overview.services

import br.com.investlog.server.overview.repositories.CapitalMonth
import br.com.investlog.server.overview.repositories.MoverCandidate
import br.com.investlog.server.overview.repositories.OverviewRepository
import br.com.investlog.server.overview.repositories.WalletSnapshotGain
import br.com.investlog.server.overview.rest.payloads.MonthSummaryResponse
import br.com.investlog.server.overview.rest.payloads.MoverResponse
import br.com.investlog.server.overview.rest.payloads.SeriesPointResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@Service
@Transactional(readOnly = true)
class OverviewService(
    private val overviewRepository: OverviewRepository,
    private val clock: Clock,
) {

    fun findSeries(userId: Long, displayCurrency: String): List<SeriesPointResponse> =
        series(
            userId = userId,
            displayCurrency = displayCurrency,
            currentMonth = YearMonth.from(today()),
            lifetimeResult = overviewRepository.findSummary(userId, displayCurrency).lifetimeResult,
            snapshotGains = overviewRepository.findWalletSnapshotGains(userId, displayCurrency),
        )

    fun findMonth(userId: Long, displayCurrency: String): MonthSummaryResponse {
        val currentMonth = YearMonth.from(today())
        val monthStart = currentMonth.atDay(1)
        val summary = overviewRepository.findSummary(userId, displayCurrency)
        val baselineGain = baselineGain(overviewRepository.findWalletSnapshotGains(userId, displayCurrency), monthStart)

        return monthSummary(
            userId = userId,
            displayCurrency = displayCurrency,
            month = currentMonth,
            currentResultChange = baselineGain?.let { gain -> summary.totalGain - gain },
            moverCandidates = overviewRepository.findMoverCandidates(userId, monthStart),
        )
    }

    fun findClosedMonth(userId: Long, displayCurrency: String, endDate: LocalDate, currentResultChange: BigDecimal?): MonthSummaryResponse {
        val month = YearMonth.from(endDate)

        return monthSummary(
            userId = userId,
            displayCurrency = displayCurrency,
            month = month,
            currentResultChange = currentResultChange,
            moverCandidates = overviewRepository.findMoverCandidatesOn(userId, month.atDay(1), endDate),
        )
    }

    private fun monthSummary(
        userId: Long,
        displayCurrency: String,
        month: YearMonth,
        currentResultChange: BigDecimal?,
        moverCandidates: List<MoverCandidate>,
    ): MonthSummaryResponse {
        val capital = overviewRepository.findCapitalSeries(userId, displayCurrency)
        val current = capital.lastOrNull { point -> !point.month.isAfter(month) }
        val previous = capital.lastOrNull { point -> point.month.isBefore(month) }
        val movers = moverCandidates
            .mapNotNull { candidate ->
                val baselinePrice = candidate.baselinePrice?.takeIf { price -> price.signum() > 0 } ?: return@mapNotNull null
                MoverResponse(
                    kind = candidate.kind,
                    ticker = candidate.ticker,
                    name = candidate.name,
                    changePct = candidate.currentPrice.divide(baselinePrice, 10, RoundingMode.HALF_UP)
                        .subtract(BigDecimal.ONE)
                        .multiply(ONE_HUNDRED),
                )
            }
            .sortedByDescending { mover -> mover.changePct }

        return MonthSummaryResponse(
            month = month.toString(),
            contributions = (current?.ownMoney ?: BigDecimal.ZERO) - (previous?.ownMoney ?: BigDecimal.ZERO),
            realizedProfit = (current?.reinvestedProfit ?: BigDecimal.ZERO) - (previous?.reinvestedProfit ?: BigDecimal.ZERO),
            currentResultChange = currentResultChange,
            bestMover = movers.firstOrNull()?.takeIf { mover -> movers.size > 1 || mover.changePct.signum() > 0 },
            worstMover = movers.lastOrNull()?.takeIf { mover -> movers.size > 1 || mover.changePct.signum() <= 0 },
        )
    }

    private fun series(
        userId: Long,
        displayCurrency: String,
        currentMonth: YearMonth,
        lifetimeResult: BigDecimal,
        snapshotGains: List<WalletSnapshotGain>,
    ): List<SeriesPointResponse> {
        val capital = overviewRepository.findCapitalSeries(userId, displayCurrency)
        if (capital.isEmpty()) return emptyList()

        val monthEndGains = monthEndGains(snapshotGains)
        val capitalByMonth = capital.associateBy { point -> point.month }
        val lastMonth = maxOf(currentMonth, capital.last().month)

        var latest: CapitalMonth = capital.first()
        return generateSequence(capital.first().month) { month -> month.plusMonths(1) }
            .takeWhile { month -> !month.isAfter(lastMonth) }
            .map { month ->
                latest = capitalByMonth[month] ?: latest
                val unrealizedGain = monthEndGains[month]
                SeriesPointResponse(
                    month = month.toString(),
                    ownMoney = latest.ownMoney,
                    reinvestedProfit = latest.reinvestedProfit,
                    totalInvested = latest.ownMoney + latest.reinvestedProfit,
                    result = when {
                        month == currentMonth -> lifetimeResult
                        unrealizedGain != null -> latest.reinvestedProfit + unrealizedGain
                        else -> latest.reinvestedProfit
                    },
                    marketValued = unrealizedGain != null || (month == currentMonth && snapshotGains.isNotEmpty()),
                )
            }
            .toList()
    }

    private fun monthEndGains(snapshotGains: List<WalletSnapshotGain>): Map<YearMonth, BigDecimal> =
        snapshotGains
            .groupBy { snapshot -> YearMonth.from(snapshot.snapshotDate) }
            .mapValues { (_, snapshotsInMonth) ->
                snapshotsInMonth
                    .groupBy { snapshot -> snapshot.walletId }
                    .values
                    .fold(BigDecimal.ZERO) { total, walletSnapshots ->
                        total + walletSnapshots.maxBy { snapshot -> snapshot.snapshotDate }.gain
                    }
            }

    private fun baselineGain(snapshotGains: List<WalletSnapshotGain>, monthStart: LocalDate): BigDecimal? {
        val monthEnd = monthStart.plusMonths(1)
        val baselines = snapshotGains
            .groupBy { snapshot -> snapshot.walletId }
            .values
            .mapNotNull { walletSnapshots ->
                walletSnapshots.filter { snapshot -> snapshot.snapshotDate.isBefore(monthStart) }
                    .maxByOrNull { snapshot -> snapshot.snapshotDate }
                    ?: walletSnapshots.filter { snapshot -> snapshot.snapshotDate.isBefore(monthEnd) }
                        .minByOrNull { snapshot -> snapshot.snapshotDate }
            }
        if (baselines.isEmpty()) return null
        return baselines.fold(BigDecimal.ZERO) { total, snapshot -> total + snapshot.gain }
    }

    private fun today(): LocalDate = LocalDate.now(clock.withZone(ZoneId.of(SCHEDULING_ZONE)))

    companion object {
        private const val SCHEDULING_ZONE = "America/Sao_Paulo"
        private val ONE_HUNDRED = BigDecimal("100")
    }
}
