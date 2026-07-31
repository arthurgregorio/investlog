package br.com.investlog.server.stockpricesync.http

import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.service.annotation.GetExchange
import java.math.BigDecimal

interface BrapiClient {

    @GetExchange("/v2/stocks/quote")
    fun getQuote(@RequestParam("symbols") ticker: String): BrapiQuoteResponse
}

data class BrapiQuoteResponse(val results: List<BrapiQuoteResult> = emptyList())

data class BrapiQuoteResult(val symbol: String, val data: BrapiQuoteData?)

data class BrapiQuoteData(val regularMarketPrice: BigDecimal?)
