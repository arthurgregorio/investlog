package br.com.investlog.server.currencyrates.rest.controllers

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.auth.rest.payloads.SessionResponse
import br.com.investlog.server.auth.rest.payloads.TotpEnrollResponse
import br.com.investlog.server.currencyrates.rest.payloads.CurrencyRateResponse
import dev.samstevens.totp.code.DefaultCodeGenerator
import org.junit.jupiter.api.Order
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test
    @Order(6)
    fun `a non-admin approved user can read the shared currency rates`() {
        val cookie = registerApproveAndLogin("currency-rates-reader@example.com", "senha123")

        val response = restTestClient.get()
            .uri("/private/v1/currency-rates")
            .header("Cookie", cookie)
            .exchange()
            .expectStatus().isOk()
            .returnResult<Map<String, Any?>>()
            .responseBody

        @Suppress("UNCHECKED_CAST")
        val content = response?.get("content") as List<Map<String, Any?>>
        assertTrue(content.any { it["currencyCode"] == "BRL" })
    }

    @Test
    @Order(7)
    fun `a non-admin is forbidden from updating a currency rate`() {
        val cookie = registerApproveAndLogin("currency-rates-writer@example.com", "senha123")

        restTestClient.put()
            .uri("/private/v1/currency-rates/BRL")
            .header("Cookie", cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"rate":1}""")
            .exchange()
            .expectStatus().isEqualTo(403)
    }

    private fun registerApproveAndLogin(email: String, password: String): String {
        restTestClient.post()
            .uri("/private/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Teste","email":"$email","password":"$password"}""")
            .exchange()
            .expectStatus().isCreated()

        val targetId = (
            restTestClient.get()
                .uri("/private/v1/users?size=200")
                .exchange()
                .expectStatus().isOk()
                .returnResult<Map<String, Any?>>()
                .responseBody
                ?.get("content") as List<*>
            )
            .map { it as Map<*, *> }
            .single { it["email"] == email }["id"] as String

        restTestClient.patch()
            .uri("/private/v1/users/$targetId/approve")
            .exchange()
            .expectStatus().isOk()

        val secret = restTestClient.post()
            .uri("/private/v1/auth/totp/enroll")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"$email","password":"$password"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<TotpEnrollResponse>()
            .responseBody
            ?.secretKey
            ?: error("Enroll did not return a secret")

        val code = DefaultCodeGenerator().generate(secret, System.currentTimeMillis() / 1000L / 30L)

        return restTestClient.post()
            .uri("/private/v1/auth/totp/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"$email","password":"$password","code":"$code"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<SessionResponse>()
            .responseHeaders
            .getFirst("Set-Cookie")
            ?.substringBefore(";")
            ?: error("Verify did not set a session cookie")
    }
}
