package br.com.investlog.server.cryptopricesync

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.cryptopricesync.scheduler.CryptoPriceSyncScheduler
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
class CryptoPriceSyncSchedulerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Autowired
    lateinit var cryptoPriceSyncScheduler: CryptoPriceSyncScheduler

    @BeforeAll
    fun setup() {
        val walletId = restTestClient.post()
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

    @AfterAll
    fun tearDown() {
        wireMockServer.stop()
    }

    @Test
    fun `scheduler skips the sync run when crypto_price_sync_enabled is false`() {
        restTestClient.patch()
            .uri("/private/v1/configurations/crypto_price_sync_enabled")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"value":"false"}""")
            .exchange()
            .expectStatus().isOk()

        cryptoPriceSyncScheduler.syncPrices()

        wireMockServer.verify(0, getRequestedFor(urlPathEqualTo("/coins/markets")))
        wireMockServer.verify(0, getRequestedFor(urlPathEqualTo("/simple/price")))
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
