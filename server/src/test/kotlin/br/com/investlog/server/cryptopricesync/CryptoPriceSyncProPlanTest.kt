package br.com.investlog.server.cryptopricesync

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingResponse
import br.com.investlog.server.cryptopricesync.services.CryptoPriceSyncService
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
class CryptoPriceSyncProPlanTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Autowired
    lateinit var cryptoPriceSyncService: CryptoPriceSyncService

    lateinit var walletId: UUID

    @BeforeAll
    fun setup() {
        walletId = restTestClient.post()
            .uri("/private/v1/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Pro Plan Wallet","kind":"crypto","currency":"BRL"}""")
            .exchange()
            .returnResult<WalletResponse>()
            .responseBody!!
            .id

        wireMockServer.stubFor(
            get(urlPathEqualTo("/coins/markets"))
                .withHeader(PRO_API_KEY_HEADER, equalTo(TEST_PRO_KEY))
                .willReturn(okJson(classpathResource("coingecko/markets-btc-response.json")))
        )
        wireMockServer.stubFor(
            get(urlPathEqualTo("/simple/price"))
                .withHeader(PRO_API_KEY_HEADER, equalTo(TEST_PRO_KEY))
                .willReturn(okJson(classpathResource("coingecko/simple-price-response.json")))
        )
        // Any request that is missing the pro header (e.g. the demo header was sent instead) falls
        // through to this catch-all and fails loudly instead of silently resolving.
        wireMockServer.stubFor(
            get(urlPathEqualTo("/coins/markets")).atPriority(10).willReturn(aResponse().withStatus(401))
        )
        wireMockServer.stubFor(
            get(urlPathEqualTo("/simple/price")).atPriority(10).willReturn(aResponse().withStatus(401))
        )
    }

    @AfterAll
    fun tearDown() {
        wireMockServer.stop()
    }

    private fun createHolding(ticker: String, currentPrice: BigDecimal): CryptoHoldingResponse =
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/crypto-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {
                  "ticker":"$ticker",
                  "name":"$ticker",
                  "currentPrice":$currentPrice,
                  "lot":{"lotDate":"2024-01-15","quantity":1,"price":$currentPrice}
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated()
            .returnResult<CryptoHoldingResponse>()
            .responseBody!!

    private fun fetchHolding(holdingId: UUID): CryptoHoldingResponse =
        restTestClient.get()
            .uri("/private/v1/wallets/$walletId/crypto-holdings/$holdingId")
            .exchange()
            .returnResult<CryptoHoldingResponse>()
            .responseBody!!

    @Test
    fun `plan set to pro sends x-cg-pro-api-key instead of the demo header`() {
        val holding = createHolding("BTC", BigDecimal("300000.00"))

        cryptoPriceSyncService.syncPrices()

        assertEquals(BigDecimal("329117"), fetchHolding(holding.id).currentPrice)
    }

    companion object {
        private const val TEST_PRO_KEY = "test-pro-key"
        private const val PRO_API_KEY_HEADER = "x-cg-pro-api-key"
        private val wireMockServer = WireMockServer(wireMockConfig().dynamicPort())

        private fun classpathResource(path: String): String =
            CryptoPriceSyncProPlanTest::class.java.classLoader.getResource(path)!!.readText()

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            wireMockServer.start()
            registry.add("investlog.coingecko.base-url") { "http://localhost:${wireMockServer.port()}" }
            registry.add("investlog.coingecko.plan") { "pro" }
            registry.add("investlog.coingecko.api-key") { TEST_PRO_KEY }
        }
    }
}
