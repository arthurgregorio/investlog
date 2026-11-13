package br.com.investlog.server.cryptopricesync.rest

import br.com.investlog.server.cryptopricesync.services.CryptoPriceSyncService
import br.com.investlog.server.shared.security.DemoModeGuard
import br.com.investlog.server.shared.security.RequiresActiveAdministrator
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/crypto-price-sync")
@RequiresActiveAdministrator
class CryptoPriceSyncController(
    private val cryptoPriceSyncService: CryptoPriceSyncService,
    private val demoModeGuard: DemoModeGuard,
) {

    @PostMapping
    fun forceSync(): ResponseEntity<Void> {
        demoModeGuard.assertNotDemoMode()
        cryptoPriceSyncService.syncPrices()
        return ResponseEntity.noContent().build()
    }
}
