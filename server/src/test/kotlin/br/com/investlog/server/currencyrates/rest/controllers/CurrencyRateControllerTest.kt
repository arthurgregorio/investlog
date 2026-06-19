package br.com.investlog.server.currencyrates.rest.controllers

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.currencyrates.rest.payloads.CurrencyRateResponse
import org.junit.jupiter.api.Order
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class CurrencyRateControllerTest : BaseIntegrationTest() {

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
            .jsonPath("$.page.totalElements").isEqualTo(2)
            .jsonPath("$.content[0].currencyCode").isEqualTo("BRL")
            .jsonPath("$.content[0].isBase").isEqualTo(true)
            .jsonPath("$.content[1].currencyCode").isEqualTo("USD")
            .jsonPath("$.content[1].isBase").isEqualTo(false)
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
            .returnResult<CurrencyRateResponse>()
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
            .returnResult<CurrencyRateResponse>()
            .responseBody

        assertEquals(true, response?.isBase)

        restTestClient.get()
            .uri("/private/v1/currency-rates")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[0].currencyCode").isEqualTo("BRL")
            .jsonPath("$.content[0].isBase").isEqualTo(false)
            .jsonPath("$.content[1].currencyCode").isEqualTo("USD")
            .jsonPath("$.content[1].isBase").isEqualTo(true)
    }

    @Test
    @Order(5)
    fun `rejects a non-positive rate with 400`() {
        restTestClient.put()
            .uri("/private/v1/currency-rates/BRL")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"rate":0}""")
            .exchange()
            .expectStatus().isBadRequest()
    }
}
