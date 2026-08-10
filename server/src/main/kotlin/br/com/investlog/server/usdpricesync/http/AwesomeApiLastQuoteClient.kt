package br.com.investlog.server.usdpricesync.http

import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.service.annotation.GetExchange
import java.math.BigDecimal

interface AwesomeApiLastQuoteClient {

    @GetExchange("/json/last/{currencyPair}")
    fun getLastQuote(@PathVariable currencyPair: String): Map<String, AwesomeApiRateEntry>
}

data class AwesomeApiRateEntry(val bid: BigDecimal)
