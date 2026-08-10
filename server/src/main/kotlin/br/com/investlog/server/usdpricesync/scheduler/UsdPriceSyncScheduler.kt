package br.com.investlog.server.usdpricesync.scheduler

import br.com.investlog.server.configurations.ConfigurationKey
import br.com.investlog.server.configurations.services.ConfigurationService
import br.com.investlog.server.usdpricesync.services.UsdPriceSyncService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class UsdPriceSyncScheduler(
    private val usdPriceSyncService: UsdPriceSyncService,
    private val configurationService: ConfigurationService,
) {

    @Scheduled(cron = "0 0 7,18 * * *", zone = "America/Sao_Paulo")
    fun syncRate() {

        if (!configurationService.isEnabled(ConfigurationKey.USD_PRICE_SYNC_ENABLED)) {
            logger.info { "USD price sync skipped: disabled via configuration" }
            return
        }

        try {
            usdPriceSyncService.syncRate()
        } catch (exception: Exception) {
            logger.error(exception) { "USD price sync run failed, next scheduled run will retry" }
        }
    }
}
