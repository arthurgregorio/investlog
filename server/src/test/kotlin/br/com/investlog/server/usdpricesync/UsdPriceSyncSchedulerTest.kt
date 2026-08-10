package br.com.investlog.server.usdpricesync

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.usdpricesync.scheduler.UsdPriceSyncScheduler
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.client.RestTestClient
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UsdPriceSyncSchedulerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Autowired
    lateinit var usdPriceSyncScheduler: UsdPriceSyncScheduler

    @AfterAll
    fun tearDown() {
        wireMockServer.stop()
    }

    @Test
    fun `scheduler skips the sync run when usd_price_sync_enabled is false`() {
        restTestClient.patch()
            .uri("/private/v1/configurations/usd_price_sync_enabled")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"value":"false"}""")
            .exchange()
            .expectStatus().isOk()

        usdPriceSyncScheduler.syncRate()

        wireMockServer.verify(0, getRequestedFor(urlPathEqualTo("/json/last/USD-BRL")))
    }

    companion object {
        private val wireMockServer = WireMockServer(wireMockConfig().dynamicPort())

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            wireMockServer.start()
            registry.add("investlog.awesomeapi.base-url") { "http://localhost:${wireMockServer.port()}" }
        }
    }
}
