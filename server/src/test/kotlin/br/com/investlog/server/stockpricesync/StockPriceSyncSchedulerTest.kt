package br.com.investlog.server.stockpricesync

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.stockpricesync.scheduler.StockPriceSyncScheduler
import br.com.investlog.server.typelists.rest.payloads.TypeResponse
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
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
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StockPriceSyncSchedulerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Autowired
    lateinit var stockPriceSyncScheduler: StockPriceSyncScheduler

    @BeforeAll
    fun setup() {
        val walletId = restTestClient.post()
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

    @AfterAll
    fun tearDown() {
        wireMockServer.stop()
    }

    @Test
    fun `scheduler skips the sync run when stock_price_sync_enabled is false`() {
        restTestClient.patch()
            .uri("/private/v1/configurations/stock_price_sync_enabled")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"value":"false"}""")
            .exchange()
            .expectStatus().isOk()

        stockPriceSyncScheduler.syncPrices()

        wireMockServer.verify(0, getRequestedFor(urlPathEqualTo("/v2/stocks/quote")))
    }

    companion object {
        private const val TEST_TOKEN = "test-token"
        private val wireMockServer = WireMockServer(wireMockConfig().dynamicPort())

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            wireMockServer.start()
            registry.add("investlog.brapi.base-url") { "http://localhost:${wireMockServer.port()}" }
            registry.add("investlog.brapi.token") { TEST_TOKEN }
        }
    }
}
