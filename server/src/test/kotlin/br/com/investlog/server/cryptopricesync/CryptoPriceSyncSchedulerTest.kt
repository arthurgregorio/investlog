package br.com.investlog.server.cryptopricesync

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.configurations.services.ConfigurationService
import br.com.investlog.server.cryptopricesync.repositories.CryptoPriceSyncRepository
import br.com.investlog.server.cryptopricesync.scheduler.CryptoPriceSyncScheduler
import br.com.investlog.server.cryptopricesync.services.CoinGeckoSymbolResolver
import br.com.investlog.server.cryptopricesync.services.CryptoPriceSyncService
import br.com.investlog.server.shared.http.coingecko.CoinGeckoClient
import br.com.investlog.server.shared.http.coingecko.CoinGeckoMarketEntry
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import com.github.tomakehurst.wiremock.WireMockServer
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
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CryptoPriceSyncSchedulerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Autowired
    lateinit var cryptoPriceSyncScheduler: CryptoPriceSyncScheduler

    @Autowired
    lateinit var configurationService: ConfigurationService

    @Autowired
    lateinit var cryptoPriceSyncRepository: CryptoPriceSyncRepository

    lateinit var walletId: UUID

    @BeforeAll
    fun setup() {
        walletId = restTestClient.post()
            .uri("/private/v1/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Scheduler Guard Wallet","kind":"crypto","currency":"BRL"}""")
            .exchange()
            .returnResult<WalletResponse>()
            .responseBody!!
            .id

        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/crypto-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {
                  "ticker":"GUARD",
                  "name":"GUARD",
                  "currentPrice":10.00,
                  "lot":{"lotDate":"2024-01-15","quantity":1,"price":10.00}
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
            .uri("/private/v1/configurations/crypto_price_sync_enabled")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"value":"$enabled"}""")
            .exchange()
            .expectStatus().isOk()
    }

    private fun currentPrice(): BigDecimal = restTestClient.get()
        .uri("/private/v1/wallets/$walletId/crypto-holdings")
        .exchange()
        .expectStatus().isOk()
        .returnResult<Map<String, Any?>>()
        .responseBody!!
        .let { page -> (page["content"] as List<*>).map { holding -> holding as Map<*, *> }.single()["currentPrice"] }
        .let { price -> BigDecimal(price.toString()) }

    @Test
    fun `scheduler skips the sync run when crypto_price_sync_enabled is false`() {
        setSyncEnabled(false)

        cryptoPriceSyncScheduler.syncPrices()

        wireMockServer.verify(0, getRequestedFor(urlPathEqualTo("/coins/markets")))
        wireMockServer.verify(0, getRequestedFor(urlPathEqualTo("/simple/price")))
    }

    @Test
    fun `scheduler runs the sync and writes the quoted price when crypto_price_sync_enabled is true`() {
        setSyncEnabled(true)
        wireMockServer.stubFor(
            get(urlPathEqualTo("/coins/markets"))
                .willReturn(okJson("""[{"id":"guard-coin","symbol":"guard"}]"""))
        )
        wireMockServer.stubFor(
            get(urlPathEqualTo("/simple/price"))
                .willReturn(okJson("""{"guard-coin":{"brl":12.34}}"""))
        )

        cryptoPriceSyncScheduler.syncPrices()

        wireMockServer.verify(1, getRequestedFor(urlPathEqualTo("/coins/markets")))
        wireMockServer.verify(1, getRequestedFor(urlPathEqualTo("/simple/price")))
        assertEquals(0, BigDecimal("12.34").compareTo(currentPrice()))
    }

    @Test
    fun `a service that throws does not propagate out of syncPrices and leaves the price untouched`() {
        setSyncEnabled(true)
        val priceBefore = currentPrice()
        val failingClient = object : CoinGeckoClient {
            override fun getMarkets(vsCurrency: String, symbols: String, order: String): List<CoinGeckoMarketEntry> =
                throw IllegalStateException("coingecko client exploded")

            override fun getPrices(ids: String, vsCurrencies: String): Map<String, Map<String, BigDecimal>> =
                throw IllegalStateException("coingecko client exploded")
        }
        val scheduler = CryptoPriceSyncScheduler(
            CryptoPriceSyncService(failingClient, CoinGeckoSymbolResolver(failingClient), cryptoPriceSyncRepository),
            configurationService,
        )

        scheduler.syncPrices()

        assertEquals(0, priceBefore.compareTo(currentPrice()))
    }

    companion object {
        private val wireMockServer = WireMockServer(wireMockConfig().dynamicPort())

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            wireMockServer.start()
            registry.add("investlog.coingecko.base-url") { "http://localhost:${wireMockServer.port()}" }
        }
    }
}
