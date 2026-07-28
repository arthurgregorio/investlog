package br.com.investlog.server.cryptopricesync.domain.services

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingResponse
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Order
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import java.util.UUID
import kotlin.test.Test

class CryptoPriceSyncServiceTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Autowired
    lateinit var cryptoPriceSyncService: CryptoPriceSyncService

    @AfterEach
    fun resetStubs() {
        coinGeckoServer.resetAll()
    }

    private fun createWallet(currency: String): UUID =
        restTestClient.post()
            .uri("/private/v1/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Crypto Wallet","kind":"CRYPTO","currency":"$currency"}""")
            .exchange()
            .returnResult<WalletResponse>()
            .responseBody!!
            .id

    private fun createHolding(walletId: UUID, ticker: String, currentPrice: String): CryptoHoldingResponse =
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/crypto-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {
                  "ticker":"$ticker",
                  "name":"$ticker",
                  "currentPrice":$currentPrice,
                  "lot":{"lotDate":"2024-01-01","quantity":1.0,"price":$currentPrice}
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated()
            .returnResult<CryptoHoldingResponse>()
            .responseBody!!

    private fun assertCurrentPrice(walletId: UUID, expectedPrice: Double) {
        restTestClient.get()
            .uri("/private/v1/wallets/$walletId/crypto-holdings")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[0].currentPrice").isEqualTo(expectedPrice)
    }

    private fun loadFixture(name: String): String =
        CryptoPriceSyncServiceTest::class.java.getResource("/coingecko/$name")!!.readText()

    @Test
    @Order(1)
    fun `updates a matching ticker and currency but leaves a ticker missing from the response untouched`() {
        val bitcoinWalletId = createWallet("USD")
        createHolding(bitcoinWalletId, "BTC", "100.00")

        val etherWalletId = createWallet("USD")
        createHolding(etherWalletId, "ETH", "100.00")

        coinGeckoServer.stubFor(
            get(urlPathEqualTo("/simple/price"))
                .willReturn(okJson(loadFixture("simple-price-response.json")))
        )

        cryptoPriceSyncService.syncPrices()

        assertCurrentPrice(bitcoinWalletId, 68000.12)
        assertCurrentPrice(etherWalletId, 100.00)
    }

    @Test
    @Order(2)
    fun `does not update anything when the CoinGecko call fails`() {
        val solanaWalletId = createWallet("USD")
        createHolding(solanaWalletId, "SOL", "50.00")

        coinGeckoServer.stubFor(
            get(urlPathEqualTo("/simple/price"))
                .willReturn(aResponse().withStatus(500))
        )

        cryptoPriceSyncService.syncPrices()

        assertCurrentPrice(solanaWalletId, 50.00)
    }

    companion object {
        private val coinGeckoServer = WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort())

        init {
            coinGeckoServer.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerCoinGeckoBaseUrl(registry: DynamicPropertyRegistry) {
            registry.add("spring.http.serviceclient.coingecko.base-url") { "http://localhost:${coinGeckoServer.port()}" }
        }

        @JvmStatic
        @AfterAll
        fun stopCoinGeckoServer() {
            coinGeckoServer.stop()
        }
    }
}
