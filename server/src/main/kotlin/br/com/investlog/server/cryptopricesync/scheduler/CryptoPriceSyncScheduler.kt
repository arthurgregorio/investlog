package br.com.investlog.server.cryptopricesync.scheduler

import br.com.investlog.server.configurations.ConfigurationKey
import br.com.investlog.server.configurations.services.ConfigurationService
import br.com.investlog.server.cryptopricesync.services.CryptoPriceSyncService
import br.com.investlog.server.shared.utils.humanReadable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

@Component
class CryptoPriceSyncScheduler(
    private val cryptoPriceSyncService: CryptoPriceSyncService,
    private val configurationService: ConfigurationService,
) {

    @Scheduled(cron = "0 0 * * * *")
    fun syncPrices() {

        if (!configurationService.isEnabled(ConfigurationKey.CRYPTO_PRICE_SYNC_ENABLED)) {
            logger.info { "Crypto price sync skipped: disabled via configuration" }
            return
        }

        try {
            cryptoPriceSyncService.syncPrices()
        } catch (exception: Exception) {
            logger.error(exception) { "Crypto price sync run failed, next scheduled run will retry" }
        }

        logger.info { "Crypto price sync run completed at ${LocalDateTime.now().humanReadable()}" }
    }
}
