package br.com.investlog.server.shared.http.brapi

import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import java.math.BigDecimal

@HttpExchange("/v2/stocks")
interface StocksClient {

    @GetExchange("/quote")
    fun getQuote(@RequestParam("symbols") ticker: String): BrapiQuoteResponse
}

data class BrapiQuoteResponse(val results: List<BrapiQuoteResult> = emptyList())

data class BrapiQuoteResult(val symbol: String, val data: BrapiQuoteData?)

data class BrapiQuoteData(val regularMarketPrice: BigDecimal?)
