package br.com.investlog.server.stockpricesync.scheduler

import br.com.investlog.server.stockpricesync.domain.services.StockPriceSyncService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class StockPriceSyncScheduler(
    private val stockPriceSyncService: StockPriceSyncService,
) {

    @Scheduled(cron = "0 0 10-18 * * MON-FRI", zone = "America/Sao_Paulo")
    fun syncPrices() {
        try {
            stockPriceSyncService.syncPrices()
        } catch (exception: Exception) {
            log.error(exception) { "Stock price sync run failed, next scheduled run will retry" }
        }
    }
}
