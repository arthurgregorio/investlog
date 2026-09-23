package br.com.investlog.server.walletdetail.rest

import br.com.investlog.server.walletdetail.rest.payloads.WalletDetailResponse
import br.com.investlog.server.walletdetail.services.WalletDetailService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/wallets/{externalId}/detail")
class WalletDetailController(private val walletDetailService: WalletDetailService) {

    @GetMapping
    fun findDetail(@PathVariable externalId: UUID): ResponseEntity<WalletDetailResponse> =
        ResponseEntity.ok(walletDetailService.findDetail(externalId))
}
