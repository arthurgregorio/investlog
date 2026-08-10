package br.com.investlog.server.usdpricesync.services

import br.com.investlog.server.currencyrates.rest.payloads.CurrencyCode
import br.com.investlog.server.currencyrates.services.CurrencyRateService
import br.com.investlog.server.shared.http.awesomeapi.LastQuoteClient
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClientException
import java.math.RoundingMode

private val logger = KotlinLogging.logger {}

@Service
@Transactional(readOnly = true)
class UsdPriceSyncService(
    private val currencyRateService: CurrencyRateService,
    private val lastQuoteClient: LastQuoteClient
) {

    @Transactional
    fun syncRate() {
        val quote = try {
            lastQuoteClient.getLastQuote("USD-BRL")["USDBRL"]
        } catch (ex: RestClientException) {
            logger.error(ex) { "Failed to fetch USD/BRL rate from AwesomeAPI, skipping this run" }
            return
        }

        if (quote == null) {
            logger.warn { "AwesomeAPI response missing USDBRL entry, skipping this run" }
            return
        }

        val roundedValue = quote.bid.setScale(2, RoundingMode.HALF_UP)
        currencyRateService.upsert(CurrencyCode.USD, roundedValue, isBase = false)

        logger.info { "USD/BRL rate sync completed: rate=$roundedValue" }
    }
}
