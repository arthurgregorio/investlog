package br.com.investlog.server.cryptopricesync.http

import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.service.annotation.GetExchange
import java.math.BigDecimal

interface CoinGeckoClient {

    @GetExchange("/simple/price")
    fun getPrices(
        @RequestParam("symbols") symbols: String,
        @RequestParam("vs_currencies") vsCurrencies: String,
    ): Map<String, Map<String, BigDecimal>>
}
