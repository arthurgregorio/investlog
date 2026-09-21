package br.com.investlog.server.walletdetail.services

import br.com.investlog.server.shared.exceptions.NotFoundException
import br.com.investlog.server.shared.security.CurrentUserProvider
import br.com.investlog.server.walletdetail.repositories.WalletDetailRepository
import br.com.investlog.server.walletdetail.repositories.WalletHolding
import br.com.investlog.server.walletdetail.repositories.WalletTransaction
import br.com.investlog.server.walletdetail.rest.payloads.WalletActivityResponse
import br.com.investlog.server.walletdetail.rest.payloads.WalletDetailResponse
import br.com.investlog.server.walletdetail.rest.payloads.WalletPerformerResponse
import br.com.investlog.server.walletdetail.rest.payloads.WalletSnapshotPointResponse
import br.com.investlog.server.wallets.repositories.WalletRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

private const val PERCENTAGE_SCALE = 10
private val HUNDRED = BigDecimal("100")

@Service
@Transactional(readOnly = true)
class WalletDetailService(
    private val currentUserProvider: CurrentUserProvider,
    private val walletRepository: WalletRepository,
    private val walletDetailRepository: WalletDetailRepository,
) {

    fun findDetail(externalId: UUID): WalletDetailResponse {
        val userId = currentUserProvider.getCurrentUser().id

        val wallet = walletRepository.findByExternalId(userId, externalId)
            ?: throw NotFoundException("Carteira $externalId não encontrada")
        val walletId = walletRepository.findInternalId(userId, externalId)
            ?: throw NotFoundException("Carteira $externalId não encontrada")

        val holdings = walletDetailRepository.findHoldings(walletId)
        val snapshots = walletDetailRepository.findSnapshots(walletId)
        val transactions = walletDetailRepository.findTransactions(walletId)

        val totalInvested = holdings.fold(BigDecimal.ZERO) { total, holding -> total + holding.costBasis }
        val currentValue = holdings.fold(BigDecimal.ZERO) { total, holding ->
            total + (holding.currentValue ?: BigDecimal.ZERO)
        }
        val gain = currentValue - totalInvested

        val performers = holdings
            .filter { holding -> holding.currentValue != null && holding.costBasis.signum() != 0 }
            .sortedBy { holding -> gainPercentageOf(holding) }

        val largestHolding = holdings.maxByOrNull { holding -> holding.currentValue ?: BigDecimal.ZERO }

        return WalletDetailResponse(
            id = wallet.id,
            name = wallet.name,
            kind = wallet.kind.text,
            currency = wallet.currency,
            currentValue = currentValue,
            totalInvested = totalInvested,
            gain = gain,
            gainPct = percentageOf(gain, totalInvested),
            series = snapshots,
            dayChange = changeOver(snapshots, 1),
            weekChange = changeOver(snapshots, 7),
            monthChange = changeOver(snapshots, 30),
            bestPerformer = performers.lastOrNull()?.let(::toPerformer),
            worstPerformer = performers.firstOrNull()?.let(::toPerformer),
            largestHoldingName = largestHolding?.name,
            largestHoldingShare = largestHolding?.let { holding ->
                percentageOf(holding.currentValue ?: BigDecimal.ZERO, currentValue)
            },
            activity = activityOf(transactions, holdings.size),
        )
    }

    private fun activityOf(transactions: List<WalletTransaction>, investmentCount: Int): WalletActivityResponse {
        val mostRecent = transactions.firstOrNull()
        val earliestDate = transactions.minByOrNull { transaction -> transaction.transactionDate }?.transactionDate

        return WalletActivityResponse(
            lastTransactionDate = mostRecent?.transactionDate,
            lastTransactionName = mostRecent?.investmentName,
            lastTransactionAmount = mostRecent?.amount,
            transactionCount = transactions.size,
            walletAgeInDays = earliestDate?.let { ChronoUnit.DAYS.between(it, LocalDate.now()) },
            investmentCount = investmentCount,
        )
    }

    private fun toPerformer(holding: WalletHolding) = WalletPerformerResponse(
        id = holding.id,
        name = holding.name,
        ticker = holding.ticker,
        kind = holding.kind,
        gain = (holding.currentValue ?: BigDecimal.ZERO) - holding.costBasis,
        gainPct = gainPercentageOf(holding),
    )

    private fun gainPercentageOf(holding: WalletHolding): BigDecimal {
        val holdingGain = (holding.currentValue ?: BigDecimal.ZERO) - holding.costBasis
        return percentageOf(holdingGain, holding.costBasis) ?: BigDecimal.ZERO
    }

    private fun percentageOf(part: BigDecimal, whole: BigDecimal): BigDecimal? =
        if (whole.signum() == 0) null
        else part.divide(whole, PERCENTAGE_SCALE, RoundingMode.HALF_UP).multiply(HUNDRED)

    private fun changeOver(snapshots: List<WalletSnapshotPointResponse>, days: Long): BigDecimal? {
        val latest = snapshots.lastOrNull() ?: return null
        val cutoff = latest.snapshotDate.minusDays(days)
        val earlier = snapshots.lastOrNull { snapshot -> snapshot.snapshotDate <= cutoff } ?: return null
        return latest.currentValue - earlier.currentValue
    }
}
