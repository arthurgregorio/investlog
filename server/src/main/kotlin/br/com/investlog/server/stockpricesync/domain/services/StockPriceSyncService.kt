package br.com.investlog.server.stockpricesync.domain.services

import br.com.investlog.server.shared.http.brapi.StocksClient
import br.com.investlog.server.stockpricesync.domain.repositories.StockPriceSyncRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClientException

private val log = KotlinLogging.logger {}

@Service
@Transactional(readOnly = true)
class StockPriceSyncService(
    private val stocksClient: StocksClient,
    private val stockPriceSyncRepository: StockPriceSyncRepository,
) {

    @Transactional
    fun syncPrices() {
        val tickers = stockPriceSyncRepository.findDistinctTickers()

        log.info { "Syncing prices for ${tickers.size} tickers" }

        for (ticker in tickers) {
            val price = try {
                stocksClient.getQuote(ticker).results.firstOrNull()?.data?.regularMarketPrice
            } catch (ex: RestClientException) {
                log.error(ex) { "Failed to fetch brapi.dev quote for ticker $ticker, keeping last-known price" }
                null
            }

            if (price == null) {
                log.warn { "No price returned by brapi.dev for ticker $ticker, keeping last-known price" }
                continue
            }

            stockPriceSyncRepository.updatePrice(ticker, price)
        }
    }
}
