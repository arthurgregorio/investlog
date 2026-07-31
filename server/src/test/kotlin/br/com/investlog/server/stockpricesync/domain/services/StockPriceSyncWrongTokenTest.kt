package br.com.investlog.server.stockpricesync.domain.services

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingResponse
import br.com.investlog.server.typelists.rest.payloads.TypeResponse
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StockPriceSyncWrongTokenTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Autowired
    lateinit var stockPriceSyncService: StockPriceSyncService

    lateinit var walletId: UUID
    lateinit var stockTypeId: UUID

    @BeforeAll
    fun setup() {
        walletId = restTestClient.post()
            .uri("/private/v1/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Stocks Wallet","kind":"stocks","currency":"BRL"}""")
            .exchange()
            .returnResult<WalletResponse>()
            .responseBody!!
            .id

        stockTypeId = restTestClient.post()
            .uri("/private/v1/stock-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Ação ON"}""")
            .exchange()
            .returnResult<TypeResponse>()
            .responseBody!!
            .id

        wireMockServer.stubFor(
            get(urlPathEqualTo("/v2/stocks/quote"))
                .withQueryParam("symbols", equalTo("WEGE3"))
                .withHeader("Authorization", equalTo("Bearer correct-token"))
                .willReturn(okJson(classpathResource("brapi/quote-wege3-response.json")))
        )
        wireMockServer.stubFor(
            get(urlPathEqualTo("/v2/stocks/quote"))
                .atPriority(10)
                .willReturn(aResponse().withStatus(401))
        )
    }

    @AfterAll
    fun tearDown() {
        wireMockServer.stop()
    }

    private fun createHolding(ticker: String, currentPrice: BigDecimal): StockHoldingResponse =
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/stock-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {
                  "stockTypeId":"$stockTypeId",
                  "ticker":"$ticker",
                  "name":"$ticker",
                  "currentPrice":$currentPrice,
                  "lot":{"lotDate":"2024-01-15","quantity":100,"price":$currentPrice}
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated()
            .returnResult<StockHoldingResponse>()
            .responseBody!!

    private fun fetchHolding(holdingId: UUID): StockHoldingResponse =
        restTestClient.get()
            .uri("/private/v1/wallets/$walletId/stock-holdings/$holdingId")
            .exchange()
            .returnResult<StockHoldingResponse>()
            .responseBody!!

    @Test
    fun `a misconfigured token never reaches the real quote stub, ticker keeps its last-known price`() {
        val holding = createHolding("WEGE3", BigDecimal("40.00"))

        stockPriceSyncService.syncPrices()

        assertEquals(BigDecimal("40.00"), fetchHolding(holding.id).currentPrice)
    }

    companion object {
        private val wireMockServer = WireMockServer(wireMockConfig().dynamicPort())

        private fun classpathResource(path: String): String =
            StockPriceSyncWrongTokenTest::class.java.classLoader.getResource(path)!!.readText()

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            wireMockServer.start()
            registry.add("investlog.brapi.base-url") { "http://localhost:${wireMockServer.port()}" }
            registry.add("investlog.brapi.token") { "wrong-token" }
        }
    }
}
