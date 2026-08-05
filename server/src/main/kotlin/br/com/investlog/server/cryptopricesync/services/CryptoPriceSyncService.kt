package br.com.investlog.server.cryptopricesync.services

import br.com.investlog.server.shared.http.coingecko.CoinGeckoClient
import br.com.investlog.server.cryptopricesync.repositories.CryptoPriceSyncRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClientException

private val logger = KotlinLogging.logger {}

@Service
@Transactional(readOnly = true)
class CryptoPriceSyncService(
    private val coinGeckoClient: CoinGeckoClient,
    private val coinGeckoSymbolResolver: CoinGeckoSymbolResolver,
    private val cryptoPriceSyncRepository: CryptoPriceSyncRepository,
) {

    @Transactional
    fun syncPrices() {
        val tickers = cryptoPriceSyncRepository.findDistinctTickers()
        val currencies = cryptoPriceSyncRepository.findDistinctCryptoWalletCurrencies()

        if (tickers.isEmpty() || currencies.isEmpty()) {
            logger.debug { "No crypto holdings to sync" }
            return
        }

        logger.debug { "Resolving ${tickers.size} ticker(s) to CoinGecko ids" }

        val tickerToCoinId = try {
            coinGeckoSymbolResolver.resolve(tickers.distinct().sorted())
        } catch (ex: RestClientException) {
            logger.error(ex) { "Failed to resolve tickers via CoinGecko, skipping this run" }
            return
        }

        val unresolved = tickers - tickerToCoinId.keys
        if (unresolved.isNotEmpty()) {
            logger.warn { "No CoinGecko match for ticker(s) ${unresolved.joinToString()}, keeping last-known price" }
        }

        if (tickerToCoinId.isEmpty()) return

        val coinIdToTicker = tickerToCoinId.entries.associate { (ticker, coinId) -> coinId to ticker }

        val prices = try {
            coinGeckoClient.getPrices(
                ids = coinIdToTicker.keys.joinToString(","),
                vsCurrencies = currencies.joinToString(",") { it.lowercase() },
            )
        } catch (ex: RestClientException) {
            logger.error(ex) { "Failed to fetch prices from CoinGecko, skipping this run" }
            return
        }

        var updatedCount = 0
        for ((coinId, pricesByCurrency) in prices) {
            val ticker = coinIdToTicker[coinId] ?: continue
            for (currency in currencies) {
                val price = pricesByCurrency[currency.lowercase()] ?: continue
                cryptoPriceSyncRepository.updatePrice(ticker, currency, price)
                updatedCount++
            }
        }

        logger.info { "Crypto price sync completed: $updatedCount ticker/currency pair(s) updated" }
    }
}
