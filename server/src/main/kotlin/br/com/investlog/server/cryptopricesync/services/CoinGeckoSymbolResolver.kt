package br.com.investlog.server.cryptopricesync.services

import br.com.investlog.server.config.core.CachingConfiguration.Companion.CRYPTO_TICKER_RESOLUTION_CACHE
import br.com.investlog.server.shared.http.coingecko.CoinGeckoClient
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class CoinGeckoSymbolResolver(
    private val coinGeckoClient: CoinGeckoClient,
) {

    @Cacheable(CRYPTO_TICKER_RESOLUTION_CACHE)
    fun resolve(tickers: List<String>): Map<String, String> {
        if (tickers.isEmpty()) return emptyMap()

        return coinGeckoClient.getMarkets(
            vsCurrency = "usd",
            symbols = tickers.joinToString(",") { it.lowercase() },
            order = "market_cap_desc",
        ).associate { it.symbol.uppercase() to it.id }
    }
}
