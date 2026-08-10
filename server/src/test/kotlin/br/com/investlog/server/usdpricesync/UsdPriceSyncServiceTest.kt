package br.com.investlog.server.usdpricesync

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.currencyrates.rest.payloads.CurrencyCode
import br.com.investlog.server.currencyrates.services.CurrencyRateService
import br.com.investlog.server.usdpricesync.services.UsdPriceSyncService
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UsdPriceSyncServiceTest : BaseIntegrationTest() {

    @Autowired
    lateinit var usdPriceSyncService: UsdPriceSyncService

    @Autowired
    lateinit var currencyRateService: CurrencyRateService

    @BeforeEach
    fun resetStubs() {
        wireMockServer.resetAll()
    }

    @AfterAll
    fun tearDown() {
        wireMockServer.stop()
    }

    private fun usdRate(): BigDecimal =
        currencyRateService.findAll(PageRequest.of(0, 10)).content
            .single { it.currencyCode == CurrencyCode.USD }
            .rate

    @Test
    @Order(1)
    fun `keeps the last-known rate when AwesomeAPI fails`() {
        wireMockServer.stubFor(
            get(urlPathEqualTo("/json/last/USD-BRL"))
                .willReturn(aResponse().withStatus(500))
        )

        usdPriceSyncService.syncRate()

        assertEquals(0, usdRate().compareTo(BigDecimal("5")))
    }

    @Test
    @Order(2)
    fun `fetches the USD-BRL quote and upserts it as a non-base currency rate`() {
        wireMockServer.stubFor(
            get(urlPathEqualTo("/json/last/USD-BRL"))
                .willReturn(okJson("""{"USDBRL":{"code":"USD","codein":"BRL","bid":"5.35"}}"""))
        )

        usdPriceSyncService.syncRate()

        assertEquals(0, usdRate().compareTo(BigDecimal("5.35")))
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
