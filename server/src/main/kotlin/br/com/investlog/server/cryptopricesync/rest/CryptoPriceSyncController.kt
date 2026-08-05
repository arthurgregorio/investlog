package br.com.investlog.server.cryptopricesync.rest

import br.com.investlog.server.cryptopricesync.services.CryptoPriceSyncService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/crypto-price-sync")
class CryptoPriceSyncController(
    private val cryptoPriceSyncService: CryptoPriceSyncService,
) {

    @PostMapping
    fun forceSync(): ResponseEntity<Void> {
        cryptoPriceSyncService.syncPrices()
        return ResponseEntity.noContent().build()
    }
}
