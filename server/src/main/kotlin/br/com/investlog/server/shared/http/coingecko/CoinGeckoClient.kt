package br.com.investlog.server.shared.http.coingecko

import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.service.annotation.GetExchange
import java.math.BigDecimal

interface CoinGeckoClient {

    @GetExchange("/coins/markets")
    fun getMarkets(
        @RequestParam("vs_currency") vsCurrency: String,
        @RequestParam("symbols") symbols: String,
        @RequestParam("order") order: String,
    ): List<CoinGeckoMarketEntry>

    @GetExchange("/simple/price")
    fun getPrices(
        @RequestParam("ids") ids: String,
        @RequestParam("vs_currencies") vsCurrencies: String,
    ): Map<String, Map<String, BigDecimal>>
}

data class CoinGeckoMarketEntry(val id: String, val symbol: String)
