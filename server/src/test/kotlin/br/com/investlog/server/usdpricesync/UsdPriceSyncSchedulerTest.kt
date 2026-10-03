package br.com.investlog.server.usdpricesync

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.configurations.services.ConfigurationService
import br.com.investlog.server.currencyrates.rest.payloads.CurrencyCode
import br.com.investlog.server.currencyrates.services.CurrencyRateService
import br.com.investlog.server.shared.http.awesomeapi.AwesomeApiRateEntry
import br.com.investlog.server.shared.http.awesomeapi.LastQuoteClient
import br.com.investlog.server.usdpricesync.scheduler.UsdPriceSyncScheduler
import br.com.investlog.server.usdpricesync.services.UsdPriceSyncService
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.client.RestTestClient
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UsdPriceSyncSchedulerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Autowired
    lateinit var usdPriceSyncScheduler: UsdPriceSyncScheduler

    @Autowired
    lateinit var configurationService: ConfigurationService

    @Autowired
    lateinit var currencyRateService: CurrencyRateService

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
            .uri("/private/v1/configurations/usd_price_sync_enabled")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"value":"$enabled"}""")
            .exchange()
            .expectStatus().isOk()
    }

    private fun usdRate(): BigDecimal =
        currencyRateService.findAll(PageRequest.of(0, 10)).content
            .single { it.currencyCode == CurrencyCode.USD }
            .rate

    @Test
    fun `scheduler skips the sync run when usd_price_sync_enabled is false`() {
        setSyncEnabled(false)

        usdPriceSyncScheduler.syncRate()

        wireMockServer.verify(0, getRequestedFor(urlPathEqualTo("/json/last/USD-BRL")))
    }

    @Test
    fun `scheduler runs the sync and stores the quoted rate when usd_price_sync_enabled is true`() {
        setSyncEnabled(true)
        wireMockServer.stubFor(
            get(urlPathEqualTo("/json/last/USD-BRL"))
                .willReturn(okJson("""{"USDBRL":{"code":"USD","codein":"BRL","bid":"5.41"}}"""))
        )

        usdPriceSyncScheduler.syncRate()

        wireMockServer.verify(1, getRequestedFor(urlPathEqualTo("/json/last/USD-BRL")))
        assertEquals(0, BigDecimal("5.41").compareTo(usdRate()))
    }

    @Test
    fun `a service that throws does not propagate out of syncRate and leaves the rate untouched`() {
        setSyncEnabled(true)
        val rateBefore = usdRate()
        val failingClient = object : LastQuoteClient {
            override fun getLastQuote(currencyPair: String): Map<String, AwesomeApiRateEntry> =
                throw IllegalStateException("awesomeapi client exploded")
        }
        val scheduler = UsdPriceSyncScheduler(UsdPriceSyncService(currencyRateService, failingClient), configurationService)

        scheduler.syncRate()

        assertEquals(0, rateBefore.compareTo(usdRate()))
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
