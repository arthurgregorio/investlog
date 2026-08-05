package br.com.investlog.server.stockpricesync.scheduler

import br.com.investlog.server.configurations.ConfigurationKey
import br.com.investlog.server.configurations.services.ConfigurationService
import br.com.investlog.server.shared.utils.humanReadable
import br.com.investlog.server.stockpricesync.services.StockPriceSyncService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

@Component
class StockPriceSyncScheduler(
    private val stockPriceSyncService: StockPriceSyncService,
    private val configurationService: ConfigurationService,
) {

    @Scheduled(cron = "0 0 10-18 * * MON-FRI", zone = "America/Sao_Paulo")
    fun syncPrices() {

        if (!configurationService.isEnabled(ConfigurationKey.STOCK_PRICE_SYNC_ENABLED)) {
            logger.info { "Stock price sync skipped: disabled via configuration" }
            return
        }

        try {
            stockPriceSyncService.syncPrices()
        } catch (exception: Exception) {
            logger.error(exception) { "Stock price sync run failed, next scheduled run will retry" }
        }

        logger.info { "Stock price sync run completed at ${LocalDateTime.now().humanReadable()}" }
    }
}
