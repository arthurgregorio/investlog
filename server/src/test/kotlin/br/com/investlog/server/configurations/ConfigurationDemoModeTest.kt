package br.com.investlog.server.configurations

import br.com.investlog.server.BaseIntegrationTest
import org.junit.jupiter.api.Order
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.client.RestTestClient
import kotlin.test.Test

@TestPropertySource(properties = ["investlog.demo-mode.enabled=true"])
class ConfigurationDemoModeTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Test
    @Order(1)
    fun `the configurations stay readable in demo mode`() {
        restTestClient.get()
            .uri(CONFIGURATIONS_URI)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[?(@.key=='stock_price_sync_enabled')].value").isEqualTo("false")
    }

    @Test
    @Order(2)
    fun `updating a configuration is refused in demo mode and changes nothing`() {
        restTestClient.patch()
            .uri("$CONFIGURATIONS_URI/stock_price_sync_enabled")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"value":"true"}""")
            .exchange()
            .expectStatus().isEqualTo(403)
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Indisponível no modo demonstração.")
            .jsonPath("$.error").isEqualTo("demo_mode")

        restTestClient.get()
            .uri(CONFIGURATIONS_URI)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[?(@.key=='stock_price_sync_enabled')].value").isEqualTo("false")
    }

    @Test
    @Order(3)
    fun `the stock price sync trigger is refused in demo mode`() {
        restTestClient.post()
            .uri("/private/v1/stock-price-sync")
            .exchange()
            .expectStatus().isEqualTo(403)
            .expectBody()
            .jsonPath("$.error").isEqualTo("demo_mode")
    }

    @Test
    @Order(4)
    fun `the crypto price sync trigger is refused in demo mode`() {
        restTestClient.post()
            .uri("/private/v1/crypto-price-sync")
            .exchange()
            .expectStatus().isEqualTo(403)
            .expectBody()
            .jsonPath("$.error").isEqualTo("demo_mode")
    }

    companion object {
        private const val CONFIGURATIONS_URI = "/private/v1/configurations"
    }
}
