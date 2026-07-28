package br.com.investlog.server.cryptopricesync.domain.services

import br.com.investlog.server.cryptopricesync.domain.repositories.CryptoPriceSyncRepository
import br.com.investlog.server.cryptopricesync.http.CoinGeckoClient
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val log = KotlinLogging.logger {}

@Service
class CryptoPriceSyncService(
    private val coinGeckoClient: CoinGeckoClient,
    private val cryptoPriceSyncRepository: CryptoPriceSyncRepository,
) {

    fun syncPrices() {
        val tickers = cryptoPriceSyncRepository.findDistinctTickers()
        val currencies = cryptoPriceSyncRepository.findDistinctCryptoWalletCurrencies()

        if (tickers.isEmpty() || currencies.isEmpty()) {
            return
        }

        val pricesByTicker = try {
            coinGeckoClient.getPrices(
                symbols = tickers.joinToString(",") { it.lowercase() },
                vsCurrencies = currencies.joinToString(",") { it.lowercase() },
            )
        } catch (exception: Exception) {
            log.error(exception) { "Crypto price sync failed calling CoinGecko" }
            return
        }

        val respondedTickers = pricesByTicker.keys.map { it.uppercase() }.toSet()
        for (ticker in tickers) {
            if (ticker.uppercase() !in respondedTickers) {
                log.warn { "CoinGecko returned no price data for ticker [$ticker]; keeping last-known price" }
            }
        }

        for ((ticker, pricesByCurrency) in pricesByTicker) {
            for ((currency, price) in pricesByCurrency) {
                cryptoPriceSyncRepository.updatePrice(ticker.uppercase(), currency.uppercase(), price)
            }
        }
    }
}
