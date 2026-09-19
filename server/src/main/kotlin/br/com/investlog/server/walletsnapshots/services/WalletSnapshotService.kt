package br.com.investlog.server.walletsnapshots.services

import br.com.investlog.server.walletsnapshots.repositories.WalletSnapshotRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

private val logger = KotlinLogging.logger {}

@Service
@Transactional(readOnly = true)
class WalletSnapshotService(private val walletSnapshotRepository: WalletSnapshotRepository) {

    @Transactional
    fun captureSnapshots(snapshotDate: LocalDate) {
        val totalsPerWallet = walletSnapshotRepository.findTotalsForEveryWallet()

        for (totals in totalsPerWallet) {
            val gain = totals.currentValue.subtract(totals.totalInvested)

            walletSnapshotRepository.upsert(
                walletId = totals.walletId,
                snapshotDate = snapshotDate,
                currentValue = totals.currentValue,
                totalInvested = totals.totalInvested,
                gain = gain,
                gainPercentage = gainPercentageOf(gain, totals.totalInvested),
            )
        }

        logger.info { "Wallet snapshot capture completed: date=$snapshotDate wallets=${totalsPerWallet.size}" }
    }

    private fun gainPercentageOf(gain: BigDecimal, totalInvested: BigDecimal): BigDecimal =
        if (totalInvested.signum() == 0) {
            BigDecimal.ZERO
        } else {
            gain.divide(totalInvested, 10, RoundingMode.HALF_UP).multiply(BigDecimal("100"))
        }
}
