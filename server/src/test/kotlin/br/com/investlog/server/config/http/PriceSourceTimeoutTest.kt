package br.com.investlog.server.config.http

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingResponse
import br.com.investlog.server.cryptopricesync.services.CryptoPriceSyncService
import br.com.investlog.server.currencyrates.rest.payloads.CurrencyCode
import br.com.investlog.server.currencyrates.services.CurrencyRateService
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingResponse
import br.com.investlog.server.stockpricesync.services.StockPriceSyncService
import br.com.investlog.server.typelists.rest.payloads.TypeResponse
import br.com.investlog.server.usdpricesync.services.UsdPriceSyncService
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PriceSourceTimeoutTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Autowired
    lateinit var stockPriceSyncService: StockPriceSyncService

    @Autowired
    lateinit var cryptoPriceSyncService: CryptoPriceSyncService

    @Autowired
    lateinit var usdPriceSyncService: UsdPriceSyncService

    @Autowired
    lateinit var currencyRateService: CurrencyRateService

    @BeforeEach
    fun resetStubs() {
        brApiServer.resetAll()
        coinGeckoServer.resetAll()
        awesomeApiServer.resetAll()
    }

    @AfterAll
    fun tearDown() {
        brApiServer.stop()
        coinGeckoServer.stop()
        awesomeApiServer.stop()
    }

    private fun createWallet(name: String, kind: String, currency: String): UUID =
        restTestClient.post()
            .uri("/private/v1/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"$name","kind":"$kind","currency":"$currency"}""")
            .exchange()
            .expectStatus().isCreated()
            .returnResult<WalletResponse>()
            .responseBody!!
            .id

    private fun createStockHolding(walletId: UUID, typeId: UUID, ticker: String): StockHoldingResponse =
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/stock-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {
                  "typeId":"$typeId",
                  "ticker":"$ticker",
                  "name":"$ticker",
                  "currentPrice":40.00,
                  "lot":{"lotDate":"2024-01-15","quantity":100,"price":40.00}
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated()
            .returnResult<StockHoldingResponse>()
            .responseBody!!

    private fun createCryptoHolding(walletId: UUID, ticker: String): CryptoHoldingResponse =
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/crypto-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {
                  "ticker":"$ticker",
                  "name":"$ticker",
                  "currentPrice":300000.00,
                  "lot":{"lotDate":"2024-01-15","quantity":1,"price":300000.00}
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated()
            .returnResult<CryptoHoldingResponse>()
            .responseBody!!

    private fun fetchStockPrice(walletId: UUID, holdingId: UUID): BigDecimal =
        restTestClient.get()
            .uri("/private/v1/wallets/$walletId/stock-holdings/$holdingId")
            .exchange()
            .returnResult<StockHoldingResponse>()
            .responseBody!!
            .currentPrice!!

    private fun fetchCryptoPrice(walletId: UUID, holdingId: UUID): BigDecimal =
        restTestClient.get()
            .uri("/private/v1/wallets/$walletId/crypto-holdings/$holdingId")
            .exchange()
            .returnResult<CryptoHoldingResponse>()
            .responseBody!!
            .currentPrice!!

    private fun usdRate(): BigDecimal =
        currencyRateService.findAll(PageRequest.of(0, 10)).content
            .single { it.currencyCode == CurrencyCode.USD }
            .rate

    private fun elapsedOf(action: () -> Unit): Duration {
        val startedAt = System.nanoTime()
        action()
        return Duration.ofNanos(System.nanoTime() - startedAt)
    }

    @Test
    fun `a brapi quote that never answers is abandoned and the other tickers still sync`() {
        val walletId = createWallet("Timeout stocks", "stocks", "BRL")
        val typeId = restTestClient.post()
            .uri("/private/v1/holding-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Ação timeout","kinds":["STOCKS"]}""")
            .exchange()
            .returnResult<TypeResponse>()
            .responseBody!!
            .id
        val hanging = createStockHolding(walletId, typeId, "HANG3")
        val healthy = createStockHolding(walletId, typeId, "WEGE3")
        brApiServer.stubFor(
            get(urlPathEqualTo("/v2/stocks/quote"))
                .withQueryParam("symbols", equalTo("HANG3"))
                .willReturn(okJson(WEGE3_QUOTE).withFixedDelay(UPSTREAM_DELAY_MILLISECONDS))
        )
        brApiServer.stubFor(
            get(urlPathEqualTo("/v2/stocks/quote"))
                .withQueryParam("symbols", equalTo("WEGE3"))
                .willReturn(okJson(WEGE3_QUOTE))
        )

        val elapsed = elapsedOf { stockPriceSyncService.syncPrices() }

        assertEquals(BigDecimal("40.00"), fetchStockPrice(walletId, hanging.id))
        assertEquals(BigDecimal("44.26"), fetchStockPrice(walletId, healthy.id))
        assertTrue(elapsed < ABANDONED_WITHIN, "the run took $elapsed")
    }

    @Test
    fun `a CoinGecko resolve call that never answers is abandoned and the run ends`() {
        val walletId = createWallet("Timeout crypto", "crypto", "BRL")
        val bitcoin = createCryptoHolding(walletId, "BTC")
        coinGeckoServer.stubFor(
            get(urlPathEqualTo("/coins/markets"))
                .willReturn(
                    okJson("""[{"id":"bitcoin","symbol":"btc","name":"Bitcoin"}]""")
                        .withFixedDelay(UPSTREAM_DELAY_MILLISECONDS)
                )
        )
        coinGeckoServer.stubFor(
            get(urlPathEqualTo("/simple/price"))
                .willReturn(okJson("""{"bitcoin":{"brl":329117}}"""))
        )

        val elapsed = elapsedOf { cryptoPriceSyncService.syncPrices() }

        assertEquals(BigDecimal("300000.00"), fetchCryptoPrice(walletId, bitcoin.id))
        assertTrue(elapsed < ABANDONED_WITHIN, "the run took $elapsed")
    }

    @Test
    fun `an AwesomeAPI quote that never answers is abandoned and the last-known rate stays`() {
        awesomeApiServer.stubFor(
            get(urlPathEqualTo("/json/last/USD-BRL"))
                .willReturn(
                    okJson("""{"USDBRL":{"code":"USD","codein":"BRL","bid":"5.35"}}""")
                        .withFixedDelay(UPSTREAM_DELAY_MILLISECONDS)
                )
        )

        val elapsed = elapsedOf { usdPriceSyncService.syncRate() }

        assertEquals(0, usdRate().compareTo(BigDecimal("5")))
        assertTrue(elapsed < ABANDONED_WITHIN, "the run took $elapsed")
    }

    companion object {
        private const val UPSTREAM_DELAY_MILLISECONDS = 6_000
        private val ABANDONED_WITHIN = Duration.ofSeconds(4)
        private const val WEGE3_QUOTE =
            """{"results":[{"requestedSymbol":"WEGE3","symbol":"WEGE3","data":{"regularMarketPrice":44.26}}]}"""

        private val brApiServer = WireMockServer(wireMockConfig().dynamicPort())
        private val coinGeckoServer = WireMockServer(wireMockConfig().dynamicPort())
        private val awesomeApiServer = WireMockServer(wireMockConfig().dynamicPort())

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            brApiServer.start()
            coinGeckoServer.start()
            awesomeApiServer.start()
            registry.add("investlog.brapi.base-url") { "http://localhost:${brApiServer.port()}" }
            registry.add("investlog.brapi.token") { "test-token" }
            registry.add("investlog.brapi.read-timeout") { "500ms" }
            registry.add("investlog.coingecko.base-url") { "http://localhost:${coinGeckoServer.port()}" }
            registry.add("investlog.coingecko.read-timeout") { "500ms" }
            registry.add("investlog.awesomeapi.base-url") { "http://localhost:${awesomeApiServer.port()}" }
            registry.add("investlog.awesomeapi.read-timeout") { "500ms" }
        }
    }
}
