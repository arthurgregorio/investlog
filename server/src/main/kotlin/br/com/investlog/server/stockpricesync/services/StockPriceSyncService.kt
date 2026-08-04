package br.com.investlog.server.stockpricesync.services

import br.com.investlog.server.shared.http.brapi.StocksClient
import br.com.investlog.server.stockpricesync.repositories.StockPriceSyncRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClientException

private val logger = KotlinLogging.logger {}

@Service
@Transactional(readOnly = true)
class StockPriceSyncService(
    private val stocksClient: StocksClient,
    private val stockPriceSyncRepository: StockPriceSyncRepository,
) {

    @Transactional
    fun syncPrices() {
        val tickers = stockPriceSyncRepository.findDistinctTickers()

        logger.debug { "Syncing prices for ${tickers.size} tickers" }

        for (ticker in tickers) {
            val price = try {
                stocksClient.getQuote(ticker).results.firstOrNull()?.data?.regularMarketPrice
            } catch (ex: RestClientException) {
                logger.error(ex) { "Failed to fetch brapi.dev quote for ticker $ticker, keeping last-known price" }
                null
            }

            if (price == null) {
                logger.warn { "No price returned by brapi.dev for ticker $ticker, keeping last-known price" }
                continue
            }

            stockPriceSyncRepository.updatePrice(ticker, price)
            logger.debug { "Price for $ticker synced" }
        }
        logger.info { "Prices for ${tickers.size} tickers synced" }
    }
}
