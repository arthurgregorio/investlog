package br.com.investlog.server.stockpricesync.rest

import br.com.investlog.server.stockpricesync.services.StockPriceSyncService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/stock-price-sync")
class StockPriceSyncController(
    private val stockPriceSyncService: StockPriceSyncService,
) {

    @PostMapping
    fun forceSync(): ResponseEntity<Void> {
        stockPriceSyncService.syncPrices()
        return ResponseEntity.noContent().build()
    }
}
