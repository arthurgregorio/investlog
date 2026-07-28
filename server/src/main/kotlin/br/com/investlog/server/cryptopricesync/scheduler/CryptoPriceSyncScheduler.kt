package br.com.investlog.server.cryptopricesync.scheduler

import br.com.investlog.server.cryptopricesync.domain.services.CryptoPriceSyncService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class CryptoPriceSyncScheduler(
    private val cryptoPriceSyncService: CryptoPriceSyncService,
) {

    @Scheduled(cron = "0 0 * * * *")
    fun syncPrices() {
        cryptoPriceSyncService.syncPrices()
    }
}
