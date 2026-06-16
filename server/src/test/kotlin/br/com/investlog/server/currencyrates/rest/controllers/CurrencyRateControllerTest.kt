package br.com.investlog.server.currencyrates.rest.controllers

import br.com.investlog.server.TestcontainersConfiguration
import br.com.investlog.server.currencyrates.rest.dtos.CurrencyRateResponse
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
@AutoConfigureRestTestClient
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class CurrencyRateControllerTest {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Test
    @Order(1)
    fun `returns the seeded currency rates ordered by currency code`() {
        restTestClient.get()
            .uri("/private/v1/currency-rates")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(3)
            .jsonPath("$.content[0].currencyCode").isEqualTo("BRL")
            .jsonPath("$.content[0].isBase").isEqualTo(true)
            .jsonPath("$.content[1].currencyCode").isEqualTo("EUR")
            .jsonPath("$.content[2].currencyCode").isEqualTo("USD")
            .jsonPath("$.content[2].isBase").isEqualTo(false)
    }

    @Test
    @Order(2)
    fun `updates an existing rate without changing the base currency`() {
        val response = restTestClient.put()
            .uri("/private/v1/currency-rates/USD")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"rate":5.50}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult(CurrencyRateResponse::class.java)
            .responseBody

        assertEquals(0, response?.rate?.compareTo(BigDecimal("5.50")))
        assertEquals(false, response?.isBase)
    }

    @Test
    @Order(3)
    fun `switches the base currency in the same transaction`() {
        val response = restTestClient.put()
            .uri("/private/v1/currency-rates/USD")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"rate":5.60,"isBase":true}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult(CurrencyRateResponse::class.java)
            .responseBody

        assertEquals(true, response?.isBase)

        restTestClient.get()
            .uri("/private/v1/currency-rates")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[0].currencyCode").isEqualTo("BRL")
            .jsonPath("$.content[0].isBase").isEqualTo(false)
            .jsonPath("$.content[2].currencyCode").isEqualTo("USD")
            .jsonPath("$.content[2].isBase").isEqualTo(true)
    }

    @Test
    @Order(4)
    fun `creates a new currency rate`() {
        val response = restTestClient.put()
            .uri("/private/v1/currency-rates/GBP")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"rate":7.0}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult(CurrencyRateResponse::class.java)
            .responseBody

        assertEquals("GBP", response?.currencyCode)
        assertEquals(0, response?.rate?.compareTo(BigDecimal("7.0")))
        assertEquals(false, response?.isBase)

        restTestClient.get()
            .uri("/private/v1/currency-rates")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(4)
    }

    @Test
    @Order(5)
    fun `rejects a non-positive rate with 400`() {
        restTestClient.put()
            .uri("/private/v1/currency-rates/JPY")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"rate":0}""")
            .exchange()
            .expectStatus().isBadRequest()
    }
}
