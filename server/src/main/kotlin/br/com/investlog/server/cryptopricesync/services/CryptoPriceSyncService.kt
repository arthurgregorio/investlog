package br.com.investlog.server.cryptopricesync.services

import br.com.investlog.server.shared.http.coingecko.CoinGeckoClient
import br.com.investlog.server.cryptopricesync.repositories.CryptoPriceSyncRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClientException
import java.math.BigDecimal

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
        val tickers = acceptable(cryptoPriceSyncRepository.findDistinctTickers(), TICKER_PATTERN, "ticker")
        val currencies = acceptable(
            cryptoPriceSyncRepository.findDistinctCryptoWalletCurrencies(),
            CURRENCY_PATTERN,
            "currency",
        )

        if (tickers.isEmpty() || currencies.isEmpty()) {
            logger.debug { "No crypto holdings to sync" }
            return
        }

        logger.debug { "Resolving ${tickers.size} ticker(s) to CoinGecko ids" }

        val tickerToCoinId = resolveTickers(tickers.distinct().sorted())

        val unresolved = tickers - tickerToCoinId.keys
        if (unresolved.isNotEmpty()) {
            logger.warn { "No CoinGecko match for ticker(s) ${unresolved.joinToString()}, keeping last-known price" }
        }

        if (tickerToCoinId.isEmpty()) return

        val coinIdToTicker = tickerToCoinId.entries.associate { (ticker, coinId) -> coinId to ticker }

        val prices = fetchPrices(coinIdToTicker.keys.joinToString(","), currencies)

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

    private fun acceptable(values: List<String>, pattern: Regex, label: String): List<String> {
        val (accepted, rejected) = values.partition { pattern.matches(it) }
        if (rejected.isNotEmpty()) {
            logger.warn { "Skipping ${rejected.size} malformed $label value(s): ${rejected.joinToString { printable(it) }}" }
        }
        return accepted
    }

    private fun resolveTickers(tickers: List<String>): Map<String, String> =
        try {
            coinGeckoSymbolResolver.resolve(tickers)
        } catch (ex: RestClientException) {
            if (tickers.size > 1 && isRejectedRequest(ex)) {
                logger.warn(ex) { "CoinGecko rejected the ticker batch, resolving ticker by ticker" }
                tickers.fold(emptyMap()) { resolved, ticker -> resolved + resolveSingleTicker(ticker) }
            } else {
                logger.error(ex) { "Failed to resolve tickers via CoinGecko, skipping this run" }
                emptyMap()
            }
        }

    private fun resolveSingleTicker(ticker: String): Map<String, String> =
        try {
            coinGeckoSymbolResolver.resolve(listOf(ticker))
        } catch (ex: RestClientException) {
            logger.warn(ex) { "Failed to resolve ticker ${printable(ticker)} via CoinGecko, skipping it" }
            emptyMap()
        }

    private fun fetchPrices(ids: String, currencies: List<String>): Map<String, Map<String, BigDecimal>> =
        try {
            coinGeckoClient.getPrices(ids = ids, vsCurrencies = currencies.joinToString(",") { it.lowercase() })
        } catch (ex: RestClientException) {
            if (currencies.size > 1 && isRejectedRequest(ex)) {
                logger.warn(ex) { "CoinGecko rejected the currency batch, fetching prices currency by currency" }
                currencies.map { currency -> fetchSingleCurrencyPrices(ids, currency) }.fold(emptyMap(), ::mergePrices)
            } else {
                logger.error(ex) { "Failed to fetch prices from CoinGecko, skipping this run" }
                emptyMap()
            }
        }

    private fun fetchSingleCurrencyPrices(ids: String, currency: String): Map<String, Map<String, BigDecimal>> =
        try {
            coinGeckoClient.getPrices(ids = ids, vsCurrencies = currency.lowercase())
        } catch (ex: RestClientException) {
            logger.warn(ex) { "Failed to fetch ${printable(currency)} prices from CoinGecko, skipping that currency" }
            emptyMap()
        }

    private fun mergePrices(
        merged: Map<String, Map<String, BigDecimal>>,
        addition: Map<String, Map<String, BigDecimal>>,
    ): Map<String, Map<String, BigDecimal>> =
        (merged.keys + addition.keys).associateWith { coinId ->
            merged[coinId].orEmpty() + addition[coinId].orEmpty()
        }

    private fun isRejectedRequest(ex: RestClientException): Boolean =
        ex is HttpClientErrorException && ex.statusCode.value() in REJECTED_REQUEST_STATUS_CODES

    private fun printable(value: String): String =
        if (value.length > MAXIMUM_LOGGED_LENGTH) value.take(MAXIMUM_LOGGED_LENGTH) + "…" else value

    companion object {
        private val TICKER_PATTERN = Regex("^[A-Za-z0-9]{1,20}$")
        private val CURRENCY_PATTERN = Regex("^[A-Za-z]{3,5}$")
        private val REJECTED_REQUEST_STATUS_CODES = setOf(400, 404, 414, 422)
        private const val MAXIMUM_LOGGED_LENGTH = 40
    }
}
