package br.com.investlog.server.cryptopricesync

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingResponse
import br.com.investlog.server.cryptopricesync.services.CryptoPriceSyncService
import br.com.investlog.server.jooq.finances.tables.references.CRYPTO_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.WALLETS
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.jooq.DSLContext
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
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
class CryptoPriceSyncIsolationTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Autowired
    lateinit var cryptoPriceSyncService: CryptoPriceSyncService

    @Autowired
    lateinit var dsl: DSLContext

    private val createdWalletIds = mutableListOf<UUID>()

    @AfterEach
    fun cleanUp() {
        wireMockServer.resetAll()
        createdWalletIds.forEach { walletId ->
            restTestClient.delete().uri("/private/v1/wallets/$walletId").exchange()
        }
        createdWalletIds.clear()
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
            .expectStatus().isCreated()
            .returnResult<WalletResponse>()
            .responseBody!!
            .id
            .also { createdWalletIds += it }

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

    private fun fetchPrice(walletId: UUID, holdingId: UUID): BigDecimal =
        restTestClient.get()
            .uri("/private/v1/wallets/$walletId/crypto-holdings/$holdingId")
            .exchange()
            .returnResult<CryptoHoldingResponse>()
            .responseBody!!
            .currentPrice!!

    private fun overwriteWalletCurrency(walletId: UUID, currency: String) {
        dsl.update(WALLETS).set(WALLETS.CURRENCY, currency).where(WALLETS.EXTERNAL_ID.eq(walletId)).execute()
    }

    private fun overwriteTicker(holdingId: UUID, ticker: String) {
        dsl.update(CRYPTO_HOLDINGS).set(CRYPTO_HOLDINGS.TICKER, ticker).where(CRYPTO_HOLDINGS.EXTERNAL_ID.eq(holdingId)).execute()
    }

    private fun stubMarkets(symbols: String, body: String) {
        wireMockServer.stubFor(
            get(urlPathEqualTo("/coins/markets"))
                .withQueryParam("symbols", equalTo(symbols))
                .willReturn(okJson(body))
        )
    }

    private fun stubMarketsRejected(symbols: String) {
        wireMockServer.stubFor(
            get(urlPathEqualTo("/coins/markets"))
                .withQueryParam("symbols", equalTo(symbols))
                .willReturn(aResponse().withStatus(400))
        )
    }

    private fun stubPrices(ids: String, vsCurrencies: String, body: String) {
        wireMockServer.stubFor(
            get(urlPathEqualTo("/simple/price"))
                .withQueryParam("ids", equalTo(ids))
                .withQueryParam("vs_currencies", equalTo(vsCurrencies))
                .willReturn(okJson(body))
        )
    }

    private fun stubPricesRejected(ids: String, vsCurrencies: String) {
        wireMockServer.stubFor(
            get(urlPathEqualTo("/simple/price"))
                .withQueryParam("ids", equalTo(ids))
                .withQueryParam("vs_currencies", equalTo(vsCurrencies))
                .willReturn(aResponse().withStatus(400))
        )
    }

    @Test
    fun `an oversized wallet currency stored before validation does not stop the other tenants' prices`() {
        val brlWalletId = createWallet("Isolation BRL", "BRL")
        val usdWalletId = createWallet("Isolation USD", "USD")
        val poisonedWalletId = createWallet("Isolation poisoned", "EUR")
        val bitcoin = createHolding(brlWalletId, "BTC", BigDecimal("300000.00"))
        val ethereum = createHolding(usdWalletId, "ETH", BigDecimal("1500.00"))
        createHolding(poisonedWalletId, "BTC", BigDecimal("1.00"))
        overwriteWalletCurrency(poisonedWalletId, "x".repeat(400))
        stubMarkets("btc,eth", MARKETS_BTC_ETH)
        stubPrices("bitcoin,ethereum", "brl,usd", PRICES_BTC_ETH)

        cryptoPriceSyncService.syncPrices()

        assertEquals(BigDecimal("329117"), fetchPrice(brlWalletId, bitcoin.id))
        assertEquals(BigDecimal("1864.45"), fetchPrice(usdWalletId, ethereum.id))
    }

    @Test
    fun `a malformed ticker stored before validation does not stop the other tenants' prices`() {
        val brlWalletId = createWallet("Isolation BRL", "BRL")
        val usdWalletId = createWallet("Isolation USD", "USD")
        val bitcoin = createHolding(brlWalletId, "BTC", BigDecimal("300000.00"))
        val ethereum = createHolding(usdWalletId, "ETH", BigDecimal("1500.00"))
        val poisoned = createHolding(brlWalletId, "SOL", BigDecimal("900.00"))
        overwriteTicker(poisoned.id, "bad ticker,with&junk" + "y".repeat(300))
        stubMarkets("btc,eth", MARKETS_BTC_ETH)
        stubPrices("bitcoin,ethereum", "brl,usd", PRICES_BTC_ETH)

        cryptoPriceSyncService.syncPrices()

        assertEquals(BigDecimal("329117"), fetchPrice(brlWalletId, bitcoin.id))
        assertEquals(BigDecimal("1864.45"), fetchPrice(usdWalletId, ethereum.id))
        assertEquals(BigDecimal("900.00"), fetchPrice(brlWalletId, poisoned.id))
    }

    @Test
    fun `a ticker CoinGecko rejects does not stop the tickers it accepts`() {
        val brlWalletId = createWallet("Isolation BRL", "BRL")
        val bitcoin = createHolding(brlWalletId, "BTC", BigDecimal("300000.00"))
        val ethereum = createHolding(brlWalletId, "ETH", BigDecimal("9000.00"))
        val rejected = createHolding(brlWalletId, "ZZZBAD", BigDecimal("1.00"))
        stubMarketsRejected("btc,eth,zzzbad")
        stubMarkets("btc", MARKETS_BTC)
        stubMarkets("eth", MARKETS_ETH)
        stubMarketsRejected("zzzbad")
        stubPrices("bitcoin,ethereum", "brl", PRICES_BTC_ETH)

        cryptoPriceSyncService.syncPrices()

        assertEquals(BigDecimal("329117"), fetchPrice(brlWalletId, bitcoin.id))
        assertEquals(BigDecimal("9593.02"), fetchPrice(brlWalletId, ethereum.id))
        assertEquals(BigDecimal("1.00"), fetchPrice(brlWalletId, rejected.id))
    }

    @Test
    fun `a currency CoinGecko rejects does not stop the currencies it accepts`() {
        val brlWalletId = createWallet("Isolation BRL", "BRL")
        val usdWalletId = createWallet("Isolation USD", "USD")
        val rejectedWalletId = createWallet("Isolation rejected", "XYZ")
        val bitcoin = createHolding(brlWalletId, "BTC", BigDecimal("300000.00"))
        val ethereum = createHolding(usdWalletId, "ETH", BigDecimal("1500.00"))
        val rejected = createHolding(rejectedWalletId, "BTC", BigDecimal("1.00"))
        stubMarkets("btc,eth", MARKETS_BTC_ETH)
        stubPricesRejected("bitcoin,ethereum", "brl,usd,xyz")
        stubPrices("bitcoin,ethereum", "brl", PRICES_BTC_ETH)
        stubPrices("bitcoin,ethereum", "usd", PRICES_BTC_ETH)
        stubPricesRejected("bitcoin,ethereum", "xyz")

        cryptoPriceSyncService.syncPrices()

        assertEquals(BigDecimal("329117"), fetchPrice(brlWalletId, bitcoin.id))
        assertEquals(BigDecimal("1864.45"), fetchPrice(usdWalletId, ethereum.id))
        assertEquals(BigDecimal("1.00"), fetchPrice(rejectedWalletId, rejected.id))
    }

    companion object {
        private val wireMockServer = WireMockServer(wireMockConfig().dynamicPort())

        private const val MARKETS_BTC_ETH =
            """[{"id":"bitcoin","symbol":"btc","name":"Bitcoin"},{"id":"ethereum","symbol":"eth","name":"Ethereum"}]"""
        private const val MARKETS_BTC = """[{"id":"bitcoin","symbol":"btc","name":"Bitcoin"}]"""
        private const val MARKETS_ETH = """[{"id":"ethereum","symbol":"eth","name":"Ethereum"}]"""
        private const val PRICES_BTC_ETH =
            """{"bitcoin":{"brl":329117,"usd":63965},"ethereum":{"brl":9593.02,"usd":1864.45}}"""

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            wireMockServer.start()
            registry.add("investlog.coingecko.base-url") { "http://localhost:${wireMockServer.port()}" }
        }
    }
}
