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
class CryptoPriceSyncServiceTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Autowired
    lateinit var cryptoPriceSyncService: CryptoPriceSyncService

    lateinit var brlWalletId: UUID
    lateinit var usdWalletId: UUID

    @BeforeAll
    fun setup() {
        brlWalletId = createWallet("BRL Crypto Wallet", "BRL")
        usdWalletId = createWallet("USD Crypto Wallet", "USD")

        // "BTC" and "ETH" both collide with several other CoinGecko-listed coins sharing the same
        // ticker symbol (e.g. "bitcoin-ai-2", "ethereum-wormhole"); the stub below returns only the
        // canonical /coins/markets pick per symbol, proving the service trusts CoinGecko's own
        // market-cap-ranked disambiguation rather than guessing.
        wireMockServer.stubFor(
            get(urlPathEqualTo("/coins/markets"))
                .withQueryParam("vs_currency", equalTo("usd"))
                .withQueryParam("symbols", equalTo("btc,eth,zzznope"))
                .withQueryParam("order", equalTo("market_cap_desc"))
                .willReturn(okJson(classpathResource("coingecko/markets-btc-eth-response.json")))
        )
        wireMockServer.stubFor(
            get(urlPathEqualTo("/simple/price"))
                .withQueryParam("ids", equalTo("bitcoin,ethereum"))
                .withQueryParam("vs_currencies", equalTo("brl,usd"))
                .willReturn(okJson(classpathResource("coingecko/simple-price-response.json")))
        )
    }

    @AfterAll
    fun tearDown() {
        wireMockServer.stop()
    }

    private fun createWallet(name: String, currency: String): UUID =
        restTestClient.post()
            .uri("/private/v1/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"$name","kind":"crypto","currency":"$currency"}""")
            .exchange()
            .returnResult<WalletResponse>()
            .responseBody!!
            .id

    private fun createHolding(walletId: UUID, ticker: String, currentPrice: BigDecimal): CryptoHoldingResponse =
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

    private fun fetchHolding(walletId: UUID, holdingId: UUID): CryptoHoldingResponse =
        restTestClient.get()
            .uri("/private/v1/wallets/$walletId/crypto-holdings/$holdingId")
            .exchange()
            .returnResult<CryptoHoldingResponse>()
            .responseBody!!

    @Test
    fun `resolves collision-prone tickers to the canonical coin and prices them in each wallet's currency, leaving unresolved tickers untouched`() {
        val bitcoinBrl = createHolding(brlWalletId, "BTC", BigDecimal("300000.00"))
        val ethereumUsd = createHolding(usdWalletId, "ETH", BigDecimal("1500.00"))
        val unresolved = createHolding(brlWalletId, "ZZZNOPE", BigDecimal("1.00"))

        cryptoPriceSyncService.syncPrices()

        assertEquals(BigDecimal("329117"), fetchHolding(brlWalletId, bitcoinBrl.id).currentPrice)
        assertEquals(BigDecimal("1864.45"), fetchHolding(usdWalletId, ethereumUsd.id).currentPrice)
        assertEquals(BigDecimal("1.00"), fetchHolding(brlWalletId, unresolved.id).currentPrice)
    }

    companion object {
        private val wireMockServer = WireMockServer(wireMockConfig().dynamicPort())

        private fun classpathResource(path: String): String =
            CryptoPriceSyncServiceTest::class.java.classLoader.getResource(path)!!.readText()

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            wireMockServer.start()
            registry.add("investlog.coingecko.base-url") { "http://localhost:${wireMockServer.port()}" }
        }
    }
}
