package br.com.investlog.server.stockpricesync.domain.services

import br.com.investlog.server.stockpricesync.domain.repositories.StockPriceSyncRepository
import br.com.investlog.server.stockpricesync.http.BrapiClient
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException

private val log = KotlinLogging.logger {}

@Service
class StockPriceSyncService(
    private val brapiClient: BrapiClient,
    private val stockPriceSyncRepository: StockPriceSyncRepository,
) {

    fun syncPrices() {
        val tickers = stockPriceSyncRepository.findDistinctTickers()

        for (ticker in tickers) {
            val price = try {
                brapiClient.getQuote(ticker).results.firstOrNull()?.regularMarketPrice
            } catch (exception: RestClientException) {
                log.warn(exception) { "Failed to fetch brapi.dev quote for ticker $ticker, keeping last-known price" }
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
