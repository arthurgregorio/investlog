package br.com.investlog.server.stockpricesync

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.configurations.services.ConfigurationService
import br.com.investlog.server.shared.http.brapi.BrapiQuoteResponse
import br.com.investlog.server.shared.http.brapi.StocksClient
import br.com.investlog.server.stockpricesync.repositories.StockPriceSyncRepository
import br.com.investlog.server.stockpricesync.scheduler.StockPriceSyncScheduler
import br.com.investlog.server.stockpricesync.services.StockPriceSyncService
import br.com.investlog.server.typelists.rest.payloads.TypeResponse
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StockPriceSyncSchedulerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Autowired
    lateinit var stockPriceSyncScheduler: StockPriceSyncScheduler

    @Autowired
    lateinit var configurationService: ConfigurationService

    @Autowired
    lateinit var stockPriceSyncRepository: StockPriceSyncRepository

    lateinit var walletId: UUID

    @BeforeAll
    fun setup() {
        walletId = restTestClient.post()
            .uri("/private/v1/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Scheduler Guard Wallet","kind":"stocks","currency":"BRL"}""")
            .exchange()
            .returnResult<WalletResponse>()
            .responseBody!!
            .id

        val stockTypeId = restTestClient.post()
            .uri("/private/v1/stock-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Ação Scheduler Guard"}""")
            .exchange()
            .returnResult<TypeResponse>()
            .responseBody!!
            .id

        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/stock-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {
                  "stockTypeId":"$stockTypeId",
                  "ticker":"GUARD3",
                  "name":"GUARD3",
                  "currentPrice":10.00,
                  "lot":{"lotDate":"2024-01-15","quantity":100,"price":10.00}
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated()
    }

    @BeforeEach
    fun resetWireMock() {
        wireMockServer.resetAll()
    }

    @AfterAll
    fun tearDown() {
        wireMockServer.stop()
    }

    private fun setSyncEnabled(enabled: Boolean) {
        restTestClient.patch()
            .uri("/private/v1/configurations/stock_price_sync_enabled")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"value":"$enabled"}""")
            .exchange()
            .expectStatus().isOk()
    }

    private fun currentPrice(): Any? = restTestClient.get()
        .uri("/private/v1/wallets/$walletId/stock-holdings")
        .exchange()
        .expectStatus().isOk()
        .returnResult<Map<String, Any?>>()
        .responseBody
        ?.let { page -> (page["content"] as List<*>).map { holding -> holding as Map<*, *> }.single()["currentPrice"] }

    @Test
    fun `scheduler skips the sync run when stock_price_sync_enabled is false`() {
        setSyncEnabled(false)

        stockPriceSyncScheduler.syncPrices()

        wireMockServer.verify(0, getRequestedFor(urlPathEqualTo("/v2/stocks/quote")))
    }

    @Test
    fun `scheduler runs the sync and writes the quoted price when stock_price_sync_enabled is true`() {
        setSyncEnabled(true)
        wireMockServer.stubFor(
            get(urlPathEqualTo("/v2/stocks/quote"))
                .withQueryParam("symbols", equalTo("GUARD3"))
                .withHeader("Authorization", equalTo("Bearer $TEST_TOKEN"))
                .willReturn(okJson(classpathResource("brapi/quote-wege3-response.json")))
        )

        stockPriceSyncScheduler.syncPrices()

        wireMockServer.verify(1, getRequestedFor(urlPathEqualTo("/v2/stocks/quote")).withQueryParam("symbols", equalTo("GUARD3")))
        assertEquals(44.26, (currentPrice() as Number).toDouble())
    }

    @Test
    fun `a service that throws does not propagate out of syncPrices and leaves the price untouched`() {
        setSyncEnabled(true)
        val priceBefore = currentPrice()
        val failingClient = object : StocksClient {
            override fun getQuote(ticker: String): BrapiQuoteResponse = throw IllegalStateException("brapi client exploded")
        }
        val scheduler = StockPriceSyncScheduler(
            StockPriceSyncService(failingClient, stockPriceSyncRepository),
            configurationService,
        )

        scheduler.syncPrices()

        assertEquals(priceBefore, currentPrice())
    }

    companion object {
        private const val TEST_TOKEN = "test-token"
        private val wireMockServer = WireMockServer(wireMockConfig().dynamicPort())

        private fun classpathResource(path: String): String =
            StockPriceSyncSchedulerTest::class.java.classLoader.getResource(path)!!.readText()

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            wireMockServer.start()
            registry.add("investlog.brapi.base-url") { "http://localhost:${wireMockServer.port()}" }
            registry.add("investlog.brapi.token") { TEST_TOKEN }
        }
    }
}
