package br.com.investlog.server.walletsnapshots.scheduler

import br.com.investlog.server.configurations.ConfigurationKey
import br.com.investlog.server.configurations.services.ConfigurationService
import br.com.investlog.server.walletsnapshots.services.WalletSnapshotService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneId

private val logger = KotlinLogging.logger {}

@Component
class WalletSnapshotScheduler(
    private val walletSnapshotService: WalletSnapshotService,
    private val configurationService: ConfigurationService,
) {

    companion object {
        private const val SCHEDULING_ZONE = "America/Sao_Paulo"
    }

    @Scheduled(cron = "0 45 23 * * *", zone = SCHEDULING_ZONE)
    fun captureSnapshots() {

        if (!configurationService.isEnabled(ConfigurationKey.WALLET_SNAPSHOT_ENABLED)) {
            logger.info { "Wallet snapshot capture skipped: disabled via configuration" }
            return
        }

        try {
            walletSnapshotService.captureSnapshots(LocalDate.now(ZoneId.of(SCHEDULING_ZONE)))
        } catch (exception: Exception) {
            logger.error(exception) { "Wallet snapshot capture failed, next scheduled run will retry" }
        }
    }
}
