package br.com.investlog.server.stockpricesync.http

import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.service.annotation.GetExchange
import java.math.BigDecimal

interface BrapiClient {

    @GetExchange("/quote/{ticker}")
    fun getQuote(@PathVariable ticker: String): BrapiQuoteResponse
}

data class BrapiQuoteResponse(val results: List<BrapiQuoteResult> = emptyList())

data class BrapiQuoteResult(val symbol: String, val regularMarketPrice: BigDecimal?)
